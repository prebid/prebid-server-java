package org.prebid.server.bidder.evolution;

import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
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

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class EvolutionBidder implements Bidder<BidRequest> {

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public EvolutionBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final HttpRequest<BidRequest> internalRequest = HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(HttpUtil.headers())
                .payload(request)
                .body(mapper.encode(request))
                .build();
        return Result.withValue(internalRequest);
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            throw new PreBidException("Empty seatbid");
        }
        return bidsFromResponse(bidResponse);
    }

    private List<BidderBid> bidsFromResponse(BidResponse bidResponse) {
        final SeatBid firstSeatBid = bidResponse.getSeatbid().get(0);
        return CollectionUtils.emptyIfNull(firstSeatBid.getBid()).stream()
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, getBidMediaType(bid.getExt()), bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private BidType getBidMediaType(JsonNode bidExt) {
        final JsonNode mediaTypeNode = bidExt != null ? bidExt.get("mediaType") : null;
        try {
            return ObjectUtils.defaultIfNull(mapper.mapper()
                    .convertValue(mediaTypeNode, BidType.class), BidType.banner);
        } catch (IllegalArgumentException e) {
            return BidType.banner;
        }
    }
}
