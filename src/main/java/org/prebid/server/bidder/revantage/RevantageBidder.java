package org.prebid.server.bidder.revantage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
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
import org.prebid.server.proto.openrtb.ext.request.revantage.ExtImpRevantage;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Prebid Server adapter for Revantage SSP.
 *
 * <p>Mirrors the Prebid.js client adapter (revantageBidAdapter.js): impressions
 * are grouped by feedId and each feed becomes a separate POST to
 * {@code https://bid.revantage.io/bid?feed=<feedId>}. The imp.ext payload is
 * rewritten to the shape expected by the upstream endpoint:
 *
 * <pre>
 * {
 *   "feedId": "...",
 *   "bidder": {
 *     "placementId": "...",   // optional
 *     "publisherId": "..."    // optional
 *   }
 * }
 * </pre>
 */
public class RevantageBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpRevantage>> REVANTAGE_EXT_TYPE_REFERENCE =
            new TypeReference<>() {};

    private static final String DEFAULT_CURRENCY = "USD";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public RevantageBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        // LinkedHashMap preserves first-seen feedId order for deterministic output.
        final Map<String, List<Imp>> impsByFeed = new LinkedHashMap<>();

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpRevantage ext = parseImpExt(imp);
                final String feedId = StringUtils.trimToNull(ext.getFeedId());
                if (feedId == null) {
                    throw new PreBidException("imp %s: missing required param feedId".formatted(imp.getId()));
                }
                final Imp rewrittenImp = rewriteImpExt(imp, feedId, ext);
                impsByFeed.computeIfAbsent(feedId, k -> new ArrayList<>()).add(rewrittenImp);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (impsByFeed.isEmpty()) {
            return Result.withErrors(errors);
        }

        final List<HttpRequest<BidRequest>> requests = new ArrayList<>(impsByFeed.size());
        for (Map.Entry<String, List<Imp>> entry : impsByFeed.entrySet()) {
            final BidRequest outgoing = request.toBuilder().imp(entry.getValue()).build();
            requests.add(buildHttpRequest(outgoing, entry.getKey()));
        }
        return Result.of(requests, errors);
    }

    private ExtImpRevantage parseImpExt(Imp imp) {
        try {
            final ExtPrebid<?, ExtImpRevantage> wrapper =
                    mapper.mapper().convertValue(imp.getExt(), REVANTAGE_EXT_TYPE_REFERENCE);
            if (wrapper == null || wrapper.getBidder() == null) {
                throw new PreBidException("imp %s: missing imp.ext.bidder".formatted(imp.getId()));
            }
            return wrapper.getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(
                    "imp %s: invalid imp.ext: %s".formatted(imp.getId(), e.getMessage()));
        }
    }

    private Imp rewriteImpExt(Imp imp, String feedId, ExtImpRevantage ext) {
        final ObjectNode bidderNode = mapper.mapper().createObjectNode();
        if (StringUtils.isNotBlank(ext.getPlacementId())) {
            bidderNode.put("placementId", ext.getPlacementId());
        }
        if (StringUtils.isNotBlank(ext.getPublisherId())) {
            bidderNode.put("publisherId", ext.getPublisherId());
        }

        final ObjectNode rewritten = mapper.mapper().createObjectNode();
        rewritten.put("feedId", feedId);
        rewritten.set("bidder", bidderNode);

        return imp.toBuilder().ext(rewritten).build();
    }

    private HttpRequest<BidRequest> buildHttpRequest(BidRequest request, String feedId) {
        final String uri = endpointUrl + "?feed=" + URLEncoder.encode(feedId, StandardCharsets.UTF_8);

        final MultiMap headers = HttpUtil.headers();
        headers.set(HttpUtil.ACCEPT_HEADER, "application/json");

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(uri)
                .headers(headers)
                .body(mapper.encodeToBytes(request))
                .impIds(collectImpIds(request))
                .payload(request)
                .build();
    }

    private static List<String> collectImpIds(BidRequest request) {
        final List<String> ids = new ArrayList<>(request.getImp().size());
        for (Imp imp : request.getImp()) {
            ids.add(imp.getId());
        }
        return ids;
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse response = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(response, bidRequest));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse response, BidRequest request) {
        if (response == null || CollectionUtils.isEmpty(response.getSeatbid())) {
            return List.of();
        }
        final String currency = StringUtils.defaultIfBlank(response.getCur(), DEFAULT_CURRENCY);
        final List<BidderBid> bids = new ArrayList<>();
        for (SeatBid seatBid : response.getSeatbid()) {
            if (seatBid == null || CollectionUtils.isEmpty(seatBid.getBid())) {
                continue;
            }
            for (Bid bid : seatBid.getBid()) {
                final BidType type = resolveMediaType(bid, request.getImp());
                bids.add(BidderBid.of(bid, type, seatBid.getSeat(), currency));
            }
        }
        return bids;
    }

    private static BidType resolveMediaType(Bid bid, List<Imp> imps) {
        if (bid.getMtype() != null) {
            switch (bid.getMtype()) {
                case 1:
                    return BidType.banner;
                case 2:
                    return BidType.video;
                default:
                    // fall through
            }
        }

        final JsonNode ext = bid.getExt();
        if (ext != null) {
            final JsonNode mediaTypeNode = ext.get("mediaType");
            if (mediaTypeNode != null && mediaTypeNode.isTextual()) {
                final String value = mediaTypeNode.asText().toLowerCase();
                if ("banner".equals(value)) {
                    return BidType.banner;
                }
                if ("video".equals(value)) {
                    return BidType.video;
                }
            }
        }

        if (isVastMarkup(bid.getAdm())) {
            return BidType.video;
        }

        for (Imp imp : imps) {
            if (!Objects.equals(imp.getId(), bid.getImpid())) {
                continue;
            }
            final boolean hasBanner = imp.getBanner() != null;
            final boolean hasVideo = imp.getVideo() != null;
            if (hasVideo && !hasBanner) {
                return BidType.video;
            }
            if (hasBanner) {
                // banner-only or multi-format with no signal — default to banner
                return BidType.banner;
            }
            break;
        }

        throw new PreBidException(
                "Cannot determine media type for bid %s on imp %s".formatted(bid.getId(), bid.getImpid()));
    }

    private static boolean isVastMarkup(String adm) {
        if (StringUtils.isBlank(adm)) {
            return false;
        }
        final String trimmed = adm.trim().toUpperCase();
        return trimmed.startsWith("<VAST") || trimmed.startsWith("<?XML");
    }
}
