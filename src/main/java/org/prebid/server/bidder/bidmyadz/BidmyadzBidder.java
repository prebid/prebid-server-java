package org.prebid.server.bidder.bidmyadz;

import com.fasterxml.jackson.databind.JsonNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
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
import java.util.stream.Collectors;

/**
 * BidMyAdz {@link Bidder} implementation.
 */
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
            return Result.withValues(extractBids(bidResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            throw new PreBidException("Empty SeatBid");
        }
        return bidsFromResponse(bidResponse);
    }

    private List<BidderBid> bidsFromResponse(BidResponse bidResponse) {
        final SeatBid seatBid = bidResponse.getSeatbid().get(0);

        if (CollectionUtils.isEmpty(seatBid.getBid())) {
            throw new PreBidException("Empty SeatBid.Bids");
        }

        return seatBid.getBid().stream()
                .map(bid -> BidderBid.of(bid, getBidType(bid.getExt()), bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private BidType getBidType(JsonNode ext) {
        try {
            return BidType.valueOf(mapper.mapper().convertValue(ext.get("mediaType"), String.class));
        } catch (Exception e) {
            throw new PreBidException(e.getMessage());
        }
    }
}
