package org.prebid.server.bidder.improvedigital;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.improvedigital.proto.ImprovedigitalBidExt;
import org.prebid.server.bidder.improvedigital.proto.ImprovedigitalBidExtImprovedigital;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.improvedigital.ExtImpImprovedigital;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ImprovedigitalBidder implements Bidder<BidRequest> {

    private static final String URL_PATH_PREFIX_MACRO = "{{PathPrefix}}";
    private static final TypeReference<ExtPrebid<?, ExtImpImprovedigital>> IMPROVEDIGITAL_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final String IS_REWARDED_INVENTORY_FIELD = "is_rewarded_inventory";
    private static final JsonPointer IS_REWARDED_INVENTORY_POINTER
            = JsonPointer.valueOf("/prebid/" + IS_REWARDED_INVENTORY_FIELD);

    private static final Pattern DEALS_PATTERN = Pattern.compile("(classic|deal)");

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
                final ExtImpImprovedigital extImp = parseImpExt(imp);
                httpRequests.add(resolveRequest(request, imp, extImp.getPublisherId()));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (!errors.isEmpty()) {
            return Result.withErrors(errors);
        }

        return Result.withValues(httpRequests);
    }

    private ExtImpImprovedigital parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), IMPROVEDIGITAL_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private static Imp updateImp(Imp imp) {
        final ObjectNode modifiedExt = imp.getExt().deepCopy();

        final JsonNode rewardedNode = modifiedExt.at(IS_REWARDED_INVENTORY_POINTER);
        if (!rewardedNode.isMissingNode() && rewardedNode.asInt() == 1) {
            modifiedExt.put(IS_REWARDED_INVENTORY_FIELD, true);
        }

        return imp.toBuilder().ext(modifiedExt).build();
    }

    private HttpRequest<BidRequest> resolveRequest(BidRequest bidRequest, Imp imp, Integer publisherId) {
        final BidRequest modifiedRequest = bidRequest.toBuilder()
                .imp(Collections.singletonList(updateImp(imp)))
                .build();

        final String pathPrefix = publisherId != null && publisherId > 0
                ? String.format("%d/", publisherId)
                : StringUtils.EMPTY;

        final String endpointUrl = this.endpointUrl.replace(URL_PATH_PREFIX_MACRO, pathPrefix);
        return BidderUtil.defaultRequest(modifiedRequest, endpointUrl, mapper);
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
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
            throw new PreBidException(
                    "Unexpected SeatBid! Must be only one but have: %d".formatted(bidResponse.getSeatbid().size()));
        }
        return bidsFromResponse(bidRequest, bidResponse);
    }

    private List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        final Map<String, Imp> impMap = bidRequest.getImp().stream()
                .collect(Collectors.toMap(Imp::getId, Function.identity()));
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> createBidderBid(bid, impMap, bidResponse.getCur()))
                .filter(Objects::nonNull)
                .toList();
    }

    private BidderBid createBidderBid(Bid bid, Map<String, Imp> impMap, String cur) {
        final Imp imp = impMap.get(bid.getImpid());
        if (imp == null) {
            throw new PreBidException(
                    "Failed to find impression for ID: \"%s\"".formatted(bid.getImpid()));
        }
        final Bid resolvedBid = resolveBid(bid, imp);
        return BidderBid.of(resolvedBid, getBidType(resolvedBid), cur);
    }

    private Bid resolveBid(Bid bid, Imp imp) {
        final Integer mtype = bid.getMtype();
        if (mtype == null && isMultiFormat(imp)) {
            throw new PreBidException(
                    "Bid must have non-null mtype for multi format impression with ID: \"%s\"".formatted(imp.getId()));
        }

        return bid.toBuilder()
                .mtype(mtype != null ? mtype : resolveMtypeFromImp(imp))
                .dealid(resolveDealId(bid))
                .build();
    }

    private String resolveDealId(Bid bid) {
        final ImprovedigitalBidExtImprovedigital bidExtImprovedigital = parseBidExtImprovedigital(bid);
        final String buyingType = bidExtImprovedigital != null ? bidExtImprovedigital.getBuyingType() : null;
        final Integer lineItemId = bidExtImprovedigital != null ? bidExtImprovedigital.getLineItemId() : null;

        return lineItemId != null && StringUtils.isNotBlank(buyingType) && DEALS_PATTERN.matcher(buyingType).matches()
                ? lineItemId.toString()
                : bid.getDealid();
    }

    private ImprovedigitalBidExtImprovedigital parseBidExtImprovedigital(Bid bid) {
        final ObjectNode bidExt = bid.getExt();
        try {
            return bidExt != null
                    ? mapper.mapper().treeToValue(bidExt, ImprovedigitalBidExt.class).getImprovedigital()
                    : null;
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private static BidType getBidType(Bid bid) {
        return switch (bid.getMtype()) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case 3 -> BidType.audio;
            case 4 -> BidType.xNative;
            case null, default -> throw new PreBidException(
                    "Unsupported mtype %d for impression with ID: \"%s\"".formatted(bid.getMtype(), bid.getImpid()));
        };
    }

    private static int resolveMtypeFromImp(Imp imp) {
        if (imp.getBanner() != null) {
            return 1;
        } else if (imp.getVideo() != null) {
            return 2;
        } else if (imp.getAudio() != null) {
            return 3;
        } else if (imp.getXNative() != null) {
            return 4;
        }
        throw new PreBidException(
                "Could not determine bid type for impression with ID: \"%s\"".formatted(imp.getId()));
    }

    private static boolean isMultiFormat(Imp imp) {
        int formatCount = 0;
        formatCount += imp.getBanner() == null ? 0 : 1;
        formatCount += imp.getVideo() == null ? 0 : 1;
        formatCount += imp.getAudio() == null ? 0 : 1;
        formatCount += imp.getXNative() == null ? 0 : 1;
        return formatCount > 1;
    }
}
