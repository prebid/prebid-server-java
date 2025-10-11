package org.prebid.server.bidder.nativery;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
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

public class NativeryBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpNativery>> NATIVERY_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final TypeReference<ExtPrebid<ExtBidPrebid, ?>> EXT_PREBID_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private static final String DEFAULT_CURRENCY = "EUR";
    private static final String NATIVERY_ERROR_HEADER = "X-Nativery-Error";

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

        final boolean isAmp = isAmpRequest(request);

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
            return Result.of(Collections.emptyList(), errors);
        }

        // 🟢 Zmienione — zamieniamy ExtRequest na ObjectNode
        final ObjectNode originalExt = request.getExt() != null
                ? mapper.mapper().convertValue(request.getExt(), ObjectNode.class)
                : null;
        final ExtRequest updatedExt = buildRequestExtWithNativery(originalExt, isAmp, widgetId);

        for (Imp imp : validImps) {
            final BidRequest singleImpRequest = request.toBuilder()
                    .imp(Collections.singletonList(imp))
                    .ext(updatedExt) // ✅ teraz typ się zgadza
                    .build();

            httpRequests.add(BidderUtil.defaultRequest(singleImpRequest, endpointUrl, mapper));
        }

        return Result.of(httpRequests, errors);
    }

    private boolean isAmpRequest(BidRequest request) {
        if (request.getSite() != null && request.getSite().getExt() != null) {
            final JsonNode siteExt = mapper.mapper().valueToTree(request.getSite().getExt());
            final JsonNode ampNode = siteExt.get("amp");
            if (ampNode != null && ampNode.asInt(0) == 1) {
                return true;
            }
        }

        return false;
    }

    private ExtImpNativery parseImpExt(Imp imp) {
        try {
            final ExtPrebid<?, ExtImpNativery> ext =
                    mapper.mapper().convertValue(imp.getExt(), NATIVERY_EXT_TYPE_REFERENCE);
            return ext != null ? ext.getBidder() : null;
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private ExtRequest buildRequestExtWithNativery(ObjectNode originalExt, boolean isAmp, String widgetId) {
        final ObjectNode root = originalExt != null
                ? originalExt.deepCopy()
                : mapper.mapper().createObjectNode();

        final ObjectNode nativeryNode = root.with("nativery");

        nativeryNode.put("isAmp", isAmp);
        if (widgetId != null) {
            nativeryNode.put("widgetId", widgetId);
        }

        return mapper.mapper().convertValue(root, ExtRequest.class);
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();

        if (httpCall.getResponse() != null && httpCall.getResponse().getStatusCode() == 204) {
            final MultiMap headers = httpCall.getResponse().getHeaders();
            final String nativeryErr = headers != null ? headers.get(NATIVERY_ERROR_HEADER) : null;
            if (StringUtils.isNotBlank(nativeryErr)) {
                return Result.withError(BidderError.badInput("Nativery Error: " + nativeryErr + "."));
            }
            return Result.withError(BidderError.badServerResponse("No Content"));
        }

        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
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
            final ObjectNode nativeryExt = extractNativeryExt(bid.getExt());
            final String mediaTypeRaw = getText(nativeryExt, "bid_ad_media_type");
            final BidType bidType = mapMediaType(mediaTypeRaw);

            final List<String> advDomains = readStringArray(nativeryExt, "bid_adv_domains");
            final Bid updatedBid = addBidMeta(bid, mediaTypeString(bidType), advDomains);

            return BidderBid.of(updatedBid, bidType, currency);
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
            return null;
        }
    }

    private ObjectNode extractNativeryExt(ObjectNode bidExt) {
        if (bidExt == null) {
            throw new PreBidException("missing bid.ext");
        }
        final JsonNode node = bidExt.get("nativery");
        if (!(node instanceof ObjectNode nativeryNode)) {
            throw new PreBidException("missing bid.ext.nativery");
        }
        return nativeryNode;
    }

    private static String getText(ObjectNode node, String field) {
        final JsonNode v = node.get(field);
        return v != null && v.isTextual() ? v.asText() : null;
    }

    private static List<String> readStringArray(ObjectNode node, String field) {
        final JsonNode arr = node.get(field);
        if (arr == null || !arr.isArray()) {
            return Collections.emptyList();
        }
        final List<String> out = new ArrayList<>();
        arr.forEach(e -> {
            if (e != null && e.isTextual()) {
                out.add(e.asText());
            }
        });
        return out;
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

    private static String mediaTypeString(BidType type) {
        return switch (type) {
            case banner -> "banner";
            case video -> "video";
            case xNative -> "native";
            default -> throw new IllegalStateException("Unexpected value: " + type);
        };
    }

    private Bid addBidMeta(Bid bid, String mediaType, List<String> advDomains) {
        final ExtBidPrebid prebid = parseExtBidPrebid(bid);

        final ExtBidPrebidMeta modifiedMeta = (prebid != null && prebid.getMeta() != null
                ? prebid.getMeta().toBuilder()
                : ExtBidPrebidMeta.builder())
                .mediaType(mediaType)
                .advertiserDomains(advDomains)
                .build();

        final ExtBidPrebid modifiedPrebid = (prebid != null ? prebid.toBuilder() : ExtBidPrebid.builder())
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
            return null;
        }
    }
}
