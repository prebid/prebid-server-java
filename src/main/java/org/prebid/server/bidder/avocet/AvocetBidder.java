package org.prebid.server.bidder.avocet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.avocet.model.AvocetResponseExt;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.EncodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AvocetBidder implements Bidder<BidRequest> {

    private static final String DEFAULT_BID_CURRENCY = "USD";
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
        if (CollectionUtils.isEmpty(request.getImp())) {
            return Result.emptyWithError(BidderError.badInput("No valid impressions in the bid request"));
        }

        String body;
        try {
            body = mapper.encode(request);
        } catch (EncodeException e) {
            final String message = String.format("Failed to encode request body, error: %s", e.getMessage());
            return Result.emptyWithError(BidderError.badInput(message));
        }

        return Result.of(Collections.singletonList(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(endpointUrl)
                        .body(body)
                        .headers(HttpUtil.headers())
                        .payload(request)
                        .build()),
                Collections.emptyList());
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        final int statusCode = httpCall.getResponse().getStatusCode();
        if (statusCode == HttpResponseStatus.NO_CONTENT.code()) {
            return Result.of(Collections.emptyList(), Collections.emptyList());
        } else if (statusCode == HttpResponseStatus.BAD_REQUEST.code()) {
            return Result.emptyWithError(BidderError.badInput("Invalid request."));
        } else if (statusCode != HttpResponseStatus.OK.code()) {
            return Result.emptyWithError(BidderError.badServerResponse(String.format("Unexpected HTTP status %s.",
                    statusCode)));
        }

        final BidResponse bidResponse;
        try {
            bidResponse = decodeBodyToBidResponse(httpCall);
        } catch (PreBidException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }

        final List<BidderBid> bidderBids = new ArrayList<>();
        for (SeatBid seatBid : bidResponse.getSeatbid()) {
            for (Bid bid : seatBid.getBid()) {
                final ObjectNode ext = bid.getExt();
                final AvocetResponseExt avocetResponseExt;
                try {
                    avocetResponseExt = parseResponseExt(ext);
                } catch (PreBidException e) {
                    return Result.emptyWithError(BidderError.badServerResponse("Invalid bid extension from endpoint."));
                }
                final BidType bidType = getBidType(bid, avocetResponseExt.getAvocetBidExtension().getDuration());
                final BidderBid bidderBid = BidderBid.of(bid, bidType, DEFAULT_BID_CURRENCY);
                bidderBids.add(bidderBid);
            }
        }
        return Result.of(bidderBids, Collections.emptyList());
    }

    private BidResponse decodeBodyToBidResponse(HttpCall<BidRequest> httpCall) {
        try {
            return mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
        } catch (DecodeException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private AvocetResponseExt parseResponseExt(ObjectNode ext) {
        if (ext == null) {
            throw new PreBidException("Invalid bid extension from endpoint.");
        }
        try {
            return mapper.mapper().treeToValue(ext, AvocetResponseExt.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private static BidType getBidType(Bid bid, Integer duration) {
        if (duration != 0) {
            return BidType.video;
        }

        final Integer api = bid.getApi();
        return api == API_FRAMEWORK_VPAID_1_0 || api == API_FRAMEWORK_VPAID_2_0 ? BidType.video : BidType.banner;
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
