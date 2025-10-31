package org.prebid.server.bidder.unruly;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.unruly.proto.UnrulyExtPrebid;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class UnrulyBidder implements Bidder<BidRequest> {

    private static final TypeReference<UnrulyExtPrebid> UNRULY_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public UnrulyBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<HttpRequest<BidRequest>> httpRequests = request.getImp().stream()
                .map(this::modifyImp)
                .map(imp -> createSingleRequest(imp, request))
                .toList();

        return Result.withValues(httpRequests);
    }

    private Imp modifyImp(Imp imp) {

        final UnrulyExtPrebid unrulyExtPrebid = parseImpExt(imp);
        return imp.toBuilder()
                .ext(mapper.mapper().valueToTree(UnrulyExtPrebid.of(
                        unrulyExtPrebid.getBidder(),
                        unrulyExtPrebid.getGpid())))
                .build();
    }

    private UnrulyExtPrebid parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), UNRULY_EXT_TYPE_REFERENCE);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private HttpRequest<BidRequest> createSingleRequest(Imp imp, BidRequest request) {
        final BidRequest outgoingRequest = request.toBuilder().imp(Collections.singletonList(imp)).build();

        return BidderUtil.defaultRequest(outgoingRequest, endpointUrl, mapper);
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse, errors), errors);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidRequest bidRequest,
                                        BidResponse bidResponse,
                                        List<BidderError> errors) {

        return bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())
                ? Collections.emptyList()
                : bidsFromResponse(bidRequest, bidResponse, errors);
    }

    private List<BidderBid> bidsFromResponse(BidRequest bidRequest,
                                             BidResponse bidResponse,
                                             List<BidderError> errors) {

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> resolveBidderBid(bid, bidResponse.getCur(), bidRequest.getImp(), errors))
                .toList();
    }

    private BidderBid resolveBidderBid(Bid bid, String currency, List<Imp> imps, List<BidderError> errors) {
        try {
            final BidType bidType = getBidType(bid.getImpid(), imps);
            return BidderBid.of(
                    bidType == BidType.video ? resolveBid(bid) : bid,
                    getBidType(bid.getImpid(), imps),
                    currency);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return BidderBid.of(bid, BidType.banner, currency);
        }
    }

    private static BidType getBidType(String impId, List<Imp> imps) {
        final List<String> unmatchedImpIds = new ArrayList<>();

        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                } else if (imp.getVideo() != null) {
                    return BidType.video;
                }
                throw new PreBidException("bid responses mediaType didn't match supported mediaTypes");
            } else {
                unmatchedImpIds.add(imp.getId());
            }
        }

        throw new PreBidException(
                "Bid response imp ID " + impId + " not found in bid request containing imps" + unmatchedImpIds);
    }

    private Bid resolveBid(Bid bid) {
        final Integer duration = bid.getDur();
        if (duration == null || duration <= 0) {
            return bid;
        }

        return bid.toBuilder().ext(resolveBidExt(duration, bid.getExt())).build();
    }

    private ObjectNode resolveBidExt(Integer duration, ObjectNode bidExt) {
        final ObjectNode bidExtUpdated = bidExt != null && !bidExt.isMissingNode()
                ? bidExt
                : mapper.mapper().createObjectNode();
        final JsonNode bidExtPrebid = resolveBidExtPrebid(duration, bidExtUpdated.get("prebid"));

        return bidExtUpdated.set("prebid", bidExtPrebid);
    }

    private ObjectNode resolveBidExtPrebid(Integer duration, JsonNode bidExtPrebid) {
        final ExtBidPrebidVideo extBidPrebidVideo = ExtBidPrebidVideo.of(duration, null);
        if (bidExtPrebid == null || bidExtPrebid.isMissingNode()) {
            return mapper.mapper().valueToTree(ExtBidPrebid.builder().video(extBidPrebidVideo).build());
        }

        final ObjectNode bidExtPrebidCasted = (ObjectNode) bidExtPrebid;
        return bidExtPrebidCasted.set("video", mapper.mapper().valueToTree(extBidPrebidVideo));
    }
}
