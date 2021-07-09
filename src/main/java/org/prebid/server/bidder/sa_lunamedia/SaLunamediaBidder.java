package org.prebid.server.bidder.sa_lunamedia;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * SaLunamedia {@link Bidder} implementation
 */
public class SaLunamediaBidder implements Bidder<BidRequest> {

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public SaLunamediaBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final HttpRequest<BidRequest> httpRequest = HttpRequest.<BidRequest>builder()
                .uri(endpointUrl)
                .method(HttpMethod.POST)
                .headers(HttpUtil.headers())
                .payload(request)
                .body(mapper.encode(request))
                .build();
        return Result.withValue(httpRequest);
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse), Collections.emptyList());
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse) {
        final List<SeatBid> seatBids = bidResponse != null ? bidResponse.getSeatbid() : null;
        if (CollectionUtils.isEmpty(seatBids)) {
            throw new PreBidException("Empty SeatBid");
        }

        final SeatBid firstSeatBid = CollectionUtils.isNotEmpty(seatBids) ? seatBids.get(0) : null;
        final List<Bid> bids = firstSeatBid != null ? firstSeatBid.getBid() : null;

        if (CollectionUtils.isEmpty(bids)) {
            throw new PreBidException("Empty SeatBid.Bids");
        }

        final Bid firstBid = bids.get(0);
        final BidType bidType;
        try {
            bidType = mapper.mapper().convertValue(firstBid.getExt(), BidType.class);
        } catch (IllegalArgumentException e) {
            throw new PreBidException("Missing BidExt");
        }

        return Collections.singletonList(BidderBid.of(firstBid, bidType, bidResponse.getCur()));
    }
}
