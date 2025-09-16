package org.prebid.server.bidder.salunamedia;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
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

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class SaLunamediaBidder implements Bidder<BidRequest> {

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public SaLunamediaBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        return Result.withValue(BidderUtil.defaultRequest(request, endpointUrl, mapper));
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse) {
        final List<SeatBid> seatBids = bidResponse != null ? bidResponse.getSeatbid() : null;
        if (CollectionUtils.isEmpty(seatBids)) {
            throw new PreBidException("Empty SeatBid");
        }

        final SeatBid firstSeatBid = seatBids.getFirst();
        final List<Bid> bids = firstSeatBid != null ? firstSeatBid.getBid() : null;
        if (CollectionUtils.isEmpty(bids)) {
            throw new PreBidException("Empty SeatBid.Bids");
        }

        final Bid firstBid = bids.getFirst();
        final ObjectNode firstBidExt = firstBid != null ? firstBid.getExt() : null;
        if (firstBidExt == null) {
            throw new PreBidException("Missing BidExt");
        }

        return Collections.singletonList(BidderBid.of(firstBid, getBidType(firstBidExt), bidResponse.getCur()));
    }

    private BidType getBidType(ObjectNode bidExt) {
        try {
            return mapper.mapper().convertValue(bidExt.get("mediaType"), BidType.class);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }
}
