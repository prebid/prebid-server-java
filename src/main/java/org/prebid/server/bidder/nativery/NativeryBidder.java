package org.prebid.server.bidder.nativery;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.Endpoint;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidServer;
import org.prebid.server.proto.openrtb.ext.request.nativery.BidExtNativery;
import org.prebid.server.proto.openrtb.ext.request.nativery.ExtImpNativery;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidMeta;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class NativeryBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpNativery>> NATIVERY_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final TypeReference<ExtPrebid<ExtBidPrebid, ?>> EXT_PREBID_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final String DEFAULT_CURRENCY = "EUR";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public NativeryBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<HttpRequest<BidRequest>> httpRequests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        final String requestEndpointName = extractEndpointName(request);
        final boolean isAmp = StringUtils.equals(requestEndpointName, Endpoint.openrtb2_amp.value());

        final List<Imp> validImps = new ArrayList<>();
        String widgetId = null;

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpNativery extImp = parseImpExt(imp);
                if (widgetId == null && StringUtils.isNotBlank(extImp.getWidgetId())) {
                    widgetId = extImp.getWidgetId();
                }
                validImps.add(imp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (validImps.isEmpty()) {
            return Result.withErrors(errors);
        }

        final ExtRequest updatedExt = buildRequestExtWithNativery(request.getExt(), isAmp, widgetId);

        for (Imp imp : validImps) {
            final BidRequest singleImpRequest = request.toBuilder()
                    .imp(Collections.singletonList(imp))
                    .ext(updatedExt)
                    .build();

            httpRequests.add(BidderUtil.defaultRequest(singleImpRequest, endpointUrl, mapper));
        }

        return Result.of(httpRequests, errors);
    }

    private static String extractEndpointName(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest)
                .map(BidRequest::getExt)
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getServer)
                .map(ExtRequestPrebidServer::getEndpoint)
                .orElse(null);
    }

    private ExtImpNativery parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), NATIVERY_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Failed to deserialize Nativery extension: " + e.getMessage());
        }
    }

    private ExtRequest buildRequestExtWithNativery(ExtRequest originalExt, boolean isAmp, String widgetId) {
        final ExtRequest ext = originalExt != null ? originalExt : ExtRequest.empty();

        final JsonNode existing = ext.getProperty("nativery");
        final ObjectNode nativeryNode = existing != null && existing.isObject()
                ? (ObjectNode) existing
                : mapper.mapper().createObjectNode();

        nativeryNode.put("isAmp", isAmp);
        if (StringUtils.isNotBlank(widgetId)) {
            nativeryNode.put("widgetId", widgetId);
        }

        ext.addProperty("nativery", nativeryNode);
        return ext;
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();

        final HttpResponse response = httpCall.getResponse();

        try {
            final BidResponse bidResponse = mapper.decodeValue(response.getBody(), BidResponse.class);
            final List<BidderBid> bidderBids = extractBids(bidResponse, errors);
            return Result.of(bidderBids, errors);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        final String currency = StringUtils.defaultIfBlank(bidResponse.getCur(), DEFAULT_CURRENCY);

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> resolveBidderBid(bid, currency, errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private BidderBid resolveBidderBid(Bid bid, String currency, List<BidderError> errors) {
        try {
            final BidExtNativery nativeryExt = parseNativeryExt(bid.getExt());

            final BidType bidType = mapMediaType(nativeryExt.getBidAdMediaType());
            final List<String> advDomains = ListUtils.emptyIfNull(
                    nativeryExt.getBidAdvDomains());

            final Bid updatedBid = addBidMeta(bid, bidType.getName(), advDomains);
            return BidderBid.of(updatedBid, bidType, currency);
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
            return null;
        }
    }

    private BidExtNativery parseNativeryExt(ObjectNode bidExt) {
        return Optional.ofNullable(bidExt)
                .map(ext -> ext.get("nativery"))
                .filter(JsonNode::isObject)
                .map(this::toBidExtNativery)
                .orElseThrow(() -> new PreBidException("missing bid.ext.nativery"));
    }

    private BidExtNativery toBidExtNativery(JsonNode node) {
        try {
            return mapper.mapper().convertValue(node, BidExtNativery.class);
        } catch (IllegalArgumentException e) {
            throw new PreBidException("invalid bid.ext.nativery: " + e.getMessage());
        }
    }

    private static BidType mapMediaType(String mediaType) {
        final String mt = StringUtils.defaultString(mediaType).toLowerCase();
        return switch (mt) {
            case "native" -> BidType.xNative;
            case "display", "banner", "rich_media" -> BidType.banner;
            case "video" -> BidType.video;
            default -> throw new PreBidException(
                    "unrecognized bid_ad_media_type in response from nativery: " + mediaType);
        };
    }

    private Bid addBidMeta(Bid bid, String mediaType, List<String> advDomains) {
        final ExtBidPrebid prebid = parseExtBidPrebid(bid);

        final ExtBidPrebidMeta modifiedMeta = Optional.ofNullable(prebid)
                .map(ExtBidPrebid::getMeta)
                .map(ExtBidPrebidMeta::toBuilder)
                .orElseGet(ExtBidPrebidMeta::builder)
                .mediaType(mediaType)
                .advertiserDomains(advDomains)
                .build();

        final ExtBidPrebid modifiedPrebid = Optional.ofNullable(prebid)
                .map(ExtBidPrebid::toBuilder)
                .orElseGet(ExtBidPrebid::builder)
                .meta(modifiedMeta)
                .build();

        return bid.toBuilder()
                .ext(mapper.mapper().valueToTree(ExtPrebid.of(modifiedPrebid, null)))
                .build();
    }

    private ExtBidPrebid parseExtBidPrebid(Bid bid) {
        try {
            return mapper.mapper()
                    .convertValue(bid.getExt(), EXT_PREBID_TYPE_REFERENCE)
                    .getPrebid();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Failed to deserialize Prebid extension: " + e.getMessage());
        }
    }
}
