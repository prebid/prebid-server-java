package org.prebid.server.bidder.clickio;

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
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ClickioBidder implements Bidder<BidRequest> {

    private static final String BIDDER_PROPERTY = "bidder";
    private static final String PARAMS_PROPERTY = "params";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public ClickioBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<Imp> modifiedImps = request.getImp().stream()
                .map(ClickioBidder::modifyImp)
                .toList();

        final BidRequest outgoingRequest = request.toBuilder().imp(modifiedImps).build();
        return Result.withValue(BidderUtil.defaultRequest(outgoingRequest, endpointUrl, mapper));
    }

    // Clickio backend reads bidder params from imp.ext.params, so imp.ext.bidder is copied there as is
    private static Imp modifyImp(Imp imp) {
        final ObjectNode impExt = imp.getExt();
        final JsonNode bidderParams = impExt != null ? impExt.get(BIDDER_PROPERTY) : null;
        if (bidderParams == null || bidderParams.isNull()) {
            return imp;
        }

        final ObjectNode modifiedImpExt = impExt.deepCopy();
        modifiedImpExt.set(PARAMS_PROPERTY, bidderParams.deepCopy());
        return imp.toBuilder().ext(modifiedImpExt).build();
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            final List<BidderError> errors = new ArrayList<>();
            final List<BidderBid> bids = extractBids(bidResponse, bidRequest.getImp(), errors);
            return Result.of(bids, errors);
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse, List<Imp> imps, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .map(bid -> makeBidderBid(bid, imps, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private static BidderBid makeBidderBid(Bid bid, List<Imp> imps, String currency, List<BidderError> errors) {
        try {
            return BidderBid.of(bid, getMediaType(bid.getImpid(), imps), currency);
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
            return null;
        }
    }

    private static BidType getMediaType(String impId, List<Imp> imps) {
        final Imp imp = imps.stream()
                .filter(candidate -> Objects.equals(candidate.getId(), impId))
                .findFirst()
                .orElseThrow(() -> new PreBidException("Failed to find impression \"%s\"".formatted(impId)));

        if (imp.getBanner() == null) {
            throw new PreBidException("Failed to resolve media type for impression \"%s\"".formatted(impId));
        }
        return BidType.banner;
    }
}
