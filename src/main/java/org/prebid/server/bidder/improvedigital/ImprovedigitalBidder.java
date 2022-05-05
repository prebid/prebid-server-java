package org.prebid.server.bidder.improvedigital;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.improvedigital.proto.ImprovedigitalBidExt;
import org.prebid.server.bidder.improvedigital.proto.ImprovedigitalBidExtImprovedigital;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ConsentedProvidersSettings;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.improvedigital.ExtImpImprovedigital;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ImprovedigitalBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpImprovedigital>> IMPROVEDIGITAL_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final String CONSENT_PROVIDERS_SETTINGS_OUT_KEY = "consented_providers_settings";
    private static final String CONSENTED_PROVIDERS_KEY = "consented_providers";
    private static final String REGEX_SPLIT_STRING_BY_DOT = "\\.";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public ImprovedigitalBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                parseAndValidateImpExt(imp);
                httpRequests.add(resolveRequest(request, imp));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (!errors.isEmpty()) {
            return Result.withErrors(errors);
        }

        return Result.withValues(httpRequests);
    }

    private ExtUser getAdditionalConsentProvidersUserExt(ExtUser extUser) {
        final String consentedProviders = ObjectUtil.getIfNotNull(
                ObjectUtil.getIfNotNull(extUser, ExtUser::getConsentedProvidersSettings),
                ConsentedProvidersSettings::getConsentedProviders);

        if (StringUtils.isBlank(consentedProviders)) {
            return extUser;
        }

        final String consentedProvidersPart = StringUtils.substringAfter(consentedProviders, "~");
        if (StringUtils.isEmpty(consentedProvidersPart)) {
            return extUser;
        }

        return fillExtUser(extUser, consentedProvidersPart.split(REGEX_SPLIT_STRING_BY_DOT));
    }

    private ExtUser fillExtUser(ExtUser extUser, String[] arrayOfSplitString) {
        final JsonNode consentProviderSettingJsonNode;
        try {
            consentProviderSettingJsonNode = customJsonNode(arrayOfSplitString);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }

        return mapper.fillExtension(extUser, consentProviderSettingJsonNode);
    }

    private JsonNode customJsonNode(String[] arrayOfSplitString) {
        final Integer[] integers = mapper.mapper().convertValue(arrayOfSplitString, Integer[].class);
        final ArrayNode arrayNode = mapper.mapper().createArrayNode();
        for (Integer integer : integers) {
            arrayNode.add(integer);
        }

        return mapper.mapper().createObjectNode().set(CONSENT_PROVIDERS_SETTINGS_OUT_KEY,
                mapper.mapper().createObjectNode().set(CONSENTED_PROVIDERS_KEY, arrayNode));
    }

    private void parseAndValidateImpExt(Imp imp) {
        final ExtImpImprovedigital ext;
        try {
            ext = mapper.mapper().convertValue(imp.getExt(), IMPROVEDIGITAL_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }

        final Integer placementId = ext.getPlacementId();
        if (placementId == null) {
            throw new PreBidException("No placementId provided");
        }
    }

    private HttpRequest<BidRequest> resolveRequest(BidRequest bidRequest, Imp imp) {
        final User user = bidRequest.getUser();
        final BidRequest modifiedRequest = bidRequest.toBuilder()
                .imp(Collections.singletonList(imp))
                .user(user != null
                        ? user.toBuilder().ext(getAdditionalConsentProvidersUserExt(user.getExt())).build()
                        : null)
                .build();

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(HttpUtil.headers())
                .payload(modifiedRequest)
                .body(mapper.encodeToBytes(modifiedRequest))
                .build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(httpCall.getRequest().getPayload(), bidResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        if (bidResponse.getSeatbid().size() > 1) {
            throw new PreBidException(String.format("Unexpected SeatBid! Must be only one but have: %d",
                    bidResponse.getSeatbid().size()));
        }
        return bidsFromResponse(bidRequest, bidResponse);
    }

    private List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bidWithDealId(bid), getBidType(bid.getImpid(), bidRequest.getImp()),
                        bidResponse.getCur()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private Bid bidWithDealId(Bid bid) {
        if (bid.getExt() == null) {
            return bid;
        }
        final ImprovedigitalBidExt improvedigitalBidExt;
        try {
            improvedigitalBidExt = mapper.mapper().treeToValue(bid.getExt(), ImprovedigitalBidExt.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage(), e);
        }
        final ImprovedigitalBidExtImprovedigital bidExtImprovedigital = improvedigitalBidExt.getImprovedigital();
        if (bidExtImprovedigital == null) {
            return bid;
        }
        // Populate dealId
        final String buyingType = bidExtImprovedigital.getBuyingType();
        final Integer lineItemId = bidExtImprovedigital.getLineItemId();
        if (!StringUtils.isBlank(buyingType)
                && buyingType.matches("(classic|deal)")
                && lineItemId != null) {
            return bid.toBuilder().dealid(lineItemId.toString()).build();
        }
        return bid;
    }

    private static BidType getBidType(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                }
                if (imp.getVideo() != null) {
                    return BidType.video;
                }
                if (imp.getXNative() != null) {
                    return BidType.xNative;
                }
                throw new PreBidException(String.format("Unknown impression type for ID: \"%s\"", impId));
            }
        }
        throw new PreBidException(String.format("Failed to find impression for ID: \"%s\"", impId));
    }
}
