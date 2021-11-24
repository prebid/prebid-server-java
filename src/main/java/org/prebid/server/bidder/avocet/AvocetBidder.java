package org.prebid.server.bidder.avocet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.avocet.model.AvocetBidExtension;
import org.prebid.server.bidder.avocet.model.AvocetResponseExt;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class AvocetBidder implements Bidder<BidRequest> {

    private static final int API_FRAMEWORK_VPAID_1_0 = 1;
    private static final int API_FRAMEWORK_VPAID_2_0 = 2;

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public AvocetBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(endpointUrl);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {

        return Result.of(Collections.singletonList(
                        HttpRequest.<BidRequest>builder()
                                .method(HttpMethod.POST)
                                .uri(endpointUrl)
                                .body(mapper.encodeToBytes(request))
                                .headers(HttpUtil.headers())
                                .payload(request)
                                .build()),
                Collections.emptyList());
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        final BidResponse bidResponse;
        try {
            bidResponse = decodeBodyToBidResponse(httpCall);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }

        final List<BidderBid> bidderBids = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();
        for (SeatBid seatBid : bidResponse.getSeatbid()) {
            for (Bid bid : seatBid.getBid()) {
                final BidType bidType;
                try {
                    bidType = getBidType(bid);
                } catch (PreBidException e) {
                    errors.add(BidderError.badServerResponse(e.getMessage()));
                    continue;
                }
                bidderBids.add(BidderBid.of(bid, bidType, bidResponse.getCur()));
            }
        }
        return Result.of(bidderBids, errors);
    }

    private BidResponse decodeBodyToBidResponse(HttpCall<BidRequest> httpCall) {
        try {
            return mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
        } catch (DecodeException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private BidType getBidType(Bid bid) {
        final Integer api = bid.getApi();
        if (api != null && (api == API_FRAMEWORK_VPAID_1_0 || api == API_FRAMEWORK_VPAID_2_0)) {
            return BidType.video;
        }

        final ObjectNode ext = bid.getExt();
        if (ext != null) {
            final AvocetResponseExt responseExt = parseResponseExt(ext);
            final AvocetBidExtension avocetExt = responseExt.getAvocet();
            final Integer duration = avocetExt != null ? avocetExt.getDuration() : null;
            if (duration != null && duration != 0) {
                return BidType.video;
            }
        }

        return BidType.banner;
    }

    private AvocetResponseExt parseResponseExt(ObjectNode ext) {
        try {
            return mapper.mapper().treeToValue(ext, AvocetResponseExt.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException("Invalid Avocet bidder bid extension", e);
        }
    }
}
