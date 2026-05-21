package org.prebid.server.bidder.revantage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpResponseStatus;
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
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
        final Map<String, List<Imp>> impsByFeed = new LinkedHashMap<>();

        for (Imp imp : request.getImp()) {
            try {
                final ExtImpRevantage ext = parseImpExt(imp);
                final String feedId = StringUtils.trimToNull(ext.getFeedId());
                if (feedId == null) {
                    errors.add(BidderError.badInput(
                            "imp %s: missing required param feedId".formatted(imp.getId())));
                    continue;
                }
                final Imp updatedImp = updateImp(imp, feedId, ext);
                impsByFeed.computeIfAbsent(feedId, k -> new ArrayList<>()).add(updatedImp);
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
            return mapper.mapper().convertValue(imp.getExt(), REVANTAGE_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(
                    "imp %s: invalid imp.ext: %s".formatted(imp.getId(), e.getMessage()));
        }
    }

    private Imp updateImp(Imp imp, String feedId, ExtImpRevantage ext) {
        final ObjectNode newExt = mapper.mapper().createObjectNode();
        newExt.put("feedId", feedId);
        final ObjectNode bidderNode = newExt.putObject("bidder");

        final String placementId = ext.getPlacementId();
        if (StringUtils.isNotBlank(placementId)) {
            bidderNode.put("placementId", placementId);
        }
        final String publisherId = ext.getPublisherId();
        if (StringUtils.isNotBlank(publisherId)) {
            bidderNode.put("publisherId", publisherId);
        }

        return imp.toBuilder().ext(newExt).build();
    }

    private HttpRequest<BidRequest> buildHttpRequest(BidRequest request, String feedId) {
        final String uri = endpointUrl + "?feed=" + HttpUtil.encodeUrl(feedId);
        return BidderUtil.defaultRequest(request, HttpUtil.headers(), uri, mapper);
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        if (httpCall.getResponse().getStatusCode() == HttpResponseStatus.NO_CONTENT.code()) {
            return Result.empty();
        }
        try {
            final BidResponse response = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(response, bidRequest));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse response, BidRequest request) {
        if (response == null || CollectionUtils.isEmpty(response.getSeatbid())) {
            return Collections.emptyList();
        }
        final String currency = StringUtils.defaultIfBlank(response.getCur(), DEFAULT_CURRENCY);
        final List<BidderBid> bids = new ArrayList<>();
        for (SeatBid seatBid : response.getSeatbid()) {
            if (seatBid == null || CollectionUtils.isEmpty(seatBid.getBid())) {
                continue;
            }
            for (Bid bid : seatBid.getBid()) {
                final BidType type = resolveBidType(bid, request.getImp());
                bids.add(BidderBid.of(bid, type, seatBid.getSeat(), currency));
            }
        }
        return bids;
    }

    private static BidType resolveBidType(Bid bid, List<Imp> imps) {
        return bidTypeFromMtype(bid.getMtype())
                .or(() -> bidTypeFromExt(bid.getExt()))
                .or(() -> bidTypeFromAdm(bid.getAdm()))
                .or(() -> bidTypeFromImp(bid.getImpid(), imps))
                .orElseThrow(() -> new PreBidException(
                        "Cannot determine media type for bid %s on imp %s"
                                .formatted(bid.getId(), bid.getImpid())));
    }

    private static Optional<BidType> bidTypeFromMtype(Integer mType) {
        return Optional.ofNullable(switch (mType) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case null, default -> null;
        });
    }

    private static Optional<BidType> bidTypeFromExt(ObjectNode bidExt) {
        return Optional.ofNullable(bidExt)
                .map(ext -> ext.get("mediaType"))
                .filter(JsonNode::isTextual)
                .map(JsonNode::asText)
                .map(String::toLowerCase)
                .map(mediaType -> switch (mediaType) {
                    case "banner" -> BidType.banner;
                    case "video" -> BidType.video;
                    default -> null;
                });
    }

    private static Optional<BidType> bidTypeFromAdm(String adm) {
        if (StringUtils.isBlank(adm)) {
            return Optional.empty();
        }
        final String trimmed = adm.trim().toUpperCase();
        return trimmed.startsWith("<VAST") || trimmed.startsWith("<?XML")
                ? Optional.of(BidType.video)
                : Optional.empty();
    }

    private static Optional<BidType> bidTypeFromImp(String impId, List<Imp> imps) {
        for (Imp imp : imps) {
            if (!Objects.equals(imp.getId(), impId)) {
                continue;
            }
            final boolean hasBanner = imp.getBanner() != null;
            final boolean hasVideo = imp.getVideo() != null;
            if (hasVideo && !hasBanner) {
                return Optional.of(BidType.video);
            }
            if (hasBanner) {
                return Optional.of(BidType.banner);
            }
            break;
        }
        return Optional.empty();
    }
}
