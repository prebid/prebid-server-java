package org.prebid.server.bidder.bidmyadz;

import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BidmyadzBidder implements Bidder<BidRequest> {

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public BidmyadzBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        if (!isValidRequest(request, errors)) {
            return Result.withErrors(errors);
        }

        return Result.withValue(HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(createHeaders())
                .payload(request)
                .body(mapper.encode(request))
                .build());
    }

    private static boolean isValidRequest(BidRequest request, List<BidderError> errors) {
        if (request.getImp().size() > 1) {
            errors.add(BidderError.badInput("Bidder does not support multi impression"));
        }
        final Device device = request.getDevice();
        final String ip = device != null ? device.getIp() : null;
        final String ipv6 = device != null ? device.getIpv6() : null;
        if (StringUtils.isEmpty(ip) && StringUtils.isEmpty(ipv6)) {
            errors.add(BidderError.badInput("IP/IPv6 is a required field"));
        }
        final String userAgent = device != null ? device.getUa() : null;
        if (StringUtils.isEmpty(userAgent)) {
            errors.add(BidderError.badInput("User-Agent is a required field"));
        }
        return errors.isEmpty();
    }

    private static MultiMap createHeaders() {
        return HttpUtil.headers().add(HttpUtil.X_OPENRTB_VERSION_HEADER, "2.5");
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValue(extractBids(bidResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private BidderBid extractBids(BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            throw new PreBidException("Empty SeatBid");
        }
        return bidsFromResponse(bidResponse);
    }

    private BidderBid bidsFromResponse(BidResponse bidResponse) {
        final List<Bid> bids = bidResponse.getSeatbid().get(0).getBid();

        if (CollectionUtils.isEmpty(bids)) {
            throw new PreBidException("Empty SeatBid.Bids");
        }

        final Bid bid = bids.get(0);
        return BidderBid.of(bid, getBidType(bid.getExt()), bidResponse.getCur());
    }

    private static BidType getBidType(JsonNode ext) {
        final JsonNode mediaTypeNode = ext != null ? ext.get("mediaType") : null;
        final String mediaType = mediaTypeNode != null && mediaTypeNode.isTextual() ? mediaTypeNode.asText() : null;
        if (StringUtils.isEmpty(mediaType)) {
            throw new PreBidException("Missed mediaType");
        }
        try {
            return BidType.valueOf(mediaType);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }
}
