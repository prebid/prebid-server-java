package org.prebid.server.bidder.hypelab;

import com.fasterxml.jackson.databind.JsonNode;
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
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.hypelab.ExtImpHypeLab;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.version.PrebidVersionProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class HypeLabBidder implements Bidder<BidRequest> {

    private static final String DISPLAY_MANAGER = "HypeLab Prebid Server";
    private static final String SOURCE = "prebid-server";
    private static final String UNKNOWN_VERSION = "unknown";

    private final String endpointUrl;
    private final JacksonMapper mapper;
    private final PrebidVersionProvider prebidVersionProvider;

    public HypeLabBidder(String endpointUrl, JacksonMapper mapper, PrebidVersionProvider prebidVersionProvider) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
        this.prebidVersionProvider = Objects.requireNonNull(prebidVersionProvider);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> validImps = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                validImps.add(makeOutgoingImp(imp));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (validImps.isEmpty()) {
            return Result.of(Collections.emptyList(), errors);
        }

        final BidRequest outgoingRequest = request.toBuilder()
                .imp(validImps)
                .ext(makeOutgoingRequestExt(request.getExt()))
                .build();

        return Result.of(Collections.singletonList(
                        HttpRequest.<BidRequest>builder()
                                .method(HttpMethod.POST)
                                .uri(endpointUrl)
                                .headers(headers())
                                .impIds(BidderUtil.impIds(outgoingRequest))
                                .body(mapper.encodeToBytes(outgoingRequest))
                                .payload(outgoingRequest)
                                .build()),
                errors);
    }

    private Imp makeOutgoingImp(Imp imp) {
        final ExtImpHypeLab extImp = parseImpExt(imp);
        final String pbsVersion = pbsVersion();

        return imp.toBuilder()
                .tagid(extImp.getPlacementSlug())
                .displaymanager(DISPLAY_MANAGER)
                .displaymanagerver(pbsVersion)
                .ext(mapper.mapper().valueToTree(ExtPrebid.of(null, extImp)))
                .build();
    }

    private ExtImpHypeLab parseImpExt(Imp imp) {
        if (imp.getExt() == null) {
            throw new PreBidException("imp %s: unable to unmarshal ext".formatted(imp.getId()));
        }

        final JsonNode bidderNode = imp.getExt().get("bidder");
        if (bidderNode == null || bidderNode.isNull()) {
            throw new PreBidException("imp %s: unable to unmarshal ext.bidder".formatted(imp.getId()));
        }

        final ExtImpHypeLab extImp;
        try {
            extImp = mapper.mapper().convertValue(bidderNode, ExtImpHypeLab.class);
        } catch (IllegalArgumentException e) {
            throw new PreBidException("imp %s: unable to unmarshal ext.bidder".formatted(imp.getId()), e);
        }

        if (StringUtils.isBlank(extImp.getPropertySlug()) || StringUtils.isBlank(extImp.getPlacementSlug())) {
            throw new PreBidException("imp %s: property_slug and placement_slug are required".formatted(imp.getId()));
        }

        return extImp;
    }

    private ExtRequest makeOutgoingRequestExt(ExtRequest ext) {
        final ExtRequest outgoingExt = ext != null ? ExtRequest.of(ext.getPrebid()) : ExtRequest.empty();
        if (ext != null) {
            outgoingExt.addProperties(ext.getProperties());
        }

        outgoingExt.addProperty("source", mapper.mapper().valueToTree(SOURCE));
        outgoingExt.addProperty("provider_version", mapper.mapper().valueToTree(pbsVersion()));

        return outgoingExt;
    }

    private static MultiMap headers() {
        return HttpUtil.headers()
                .add(HttpUtil.X_OPENRTB_VERSION_HEADER, "2.6");
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        final BidResponse bidResponse;
        try {
            bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }

        final List<BidderError> errors = new ArrayList<>();
        return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse, errors), errors);
    }

    private static List<BidderBid> extractBids(BidRequest request, BidResponse response, List<BidderError> errors) {
        if (response == null || CollectionUtils.isEmpty(response.getSeatbid())) {
            return Collections.emptyList();
        }

        final Map<String, Imp> impIdToImp = request.getImp().stream()
                .collect(Collectors.toMap(Imp::getId, imp -> imp));

        return response.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(seatBid -> bidsFromSeatBid(seatBid, impIdToImp, response.getCur(), errors))
                .flatMap(Collection::stream)
                .toList();
    }

    private static List<BidderBid> bidsFromSeatBid(SeatBid seatBid, Map<String, Imp> impIdToImp, String currency,
                                                   List<BidderError> errors) {

        final List<Bid> bids = seatBid.getBid();
        if (CollectionUtils.isEmpty(bids)) {
            return Collections.emptyList();
        }

        return bids.stream()
                .filter(Objects::nonNull)
                .map(bid -> makeBidderBid(bid, seatBid.getSeat(), impIdToImp, currency, errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private static BidderBid makeBidderBid(Bid bid, String seat, Map<String, Imp> impIdToImp, String currency,
                                           List<BidderError> errors) {

        try {
            return BidderBid.of(bid, resolveBidType(bid, impIdToImp), seat, currency);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
    }

    private static BidType resolveBidType(Bid bid, Map<String, Imp> impIdToImp) {
        final BidType bidTypeFromMtype = bidTypeFromMtype(bid.getMtype());
        if (bidTypeFromMtype != null) {
            return bidTypeFromMtype;
        }

        final BidType bidTypeFromExt = bidTypeFromExt(bid);
        if (bidTypeFromExt != null) {
            return bidTypeFromExt;
        }

        if (StringUtils.startsWith(StringUtils.trimToEmpty(bid.getAdm()), "<VAST")) {
            return BidType.video;
        }

        if (impIdToImp.containsKey(bid.getImpid())) {
            return BidderUtil.getBidType(bid, impIdToImp);
        }

        throw new PreBidException("unable to determine media type for bid %s on imp %s"
                .formatted(bid.getId(), bid.getImpid()));
    }

    private static BidType bidTypeFromMtype(Integer mtype) {
        return switch (Objects.requireNonNullElse(mtype, 0)) {
            case 1 -> BidType.banner;
            case 2 -> BidType.video;
            case 4 -> BidType.xNative;
            default -> null;
        };
    }

    private static BidType bidTypeFromExt(Bid bid) {
        final JsonNode hypelabExt = bid.getExt() != null ? bid.getExt().get("hypelab") : null;
        final String creativeType = hypelabExt != null ? hypelabExt.path("creative_type").asText() : null;

        return switch (StringUtils.defaultString(creativeType)) {
            case "display" -> BidType.banner;
            case "video" -> BidType.video;
            default -> null;
        };
    }

    private String pbsVersion() {
        return StringUtils.defaultIfBlank(prebidVersionProvider.getNameVersionRecord(), UNKNOWN_VERSION);
    }
}
