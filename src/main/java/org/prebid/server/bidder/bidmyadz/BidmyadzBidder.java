package org.prebid.server.bidder.bidmyadz;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
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
import org.prebid.server.json.EncodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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

        try {
            return Result.withValue(HttpRequest.<BidRequest>builder()
                    .method(HttpMethod.POST)
                    .uri(endpointUrl)
                    .headers(HttpUtil.headers().add(HttpUtil.X_OPENRTB_VERSION_HEADER, "2.5"))
                    .payload(request)
                    .body(mapper.encode(request))
                    .build());
        } catch (EncodeException e) {
            return Result.withError(
                    BidderError.badInput(String.format("Failed to encode request body, error: %s", e.getMessage())));
        }
    }

    private boolean isValidRequest(BidRequest request, List<BidderError> errors) {
        if (request.getImp().size() > 1) {
            errors.add(BidderError.badInput("Bidder does not support multi impression"));
        }
        final Device device = request.getDevice();
        if (device == null || !hasValidIp(device)) {
            errors.add(BidderError.badInput("IP/IPv6 is a required field"));
        }

        if (device == null || StringUtils.isEmpty(device.getUa())) {
            errors.add(BidderError.badInput("User-Agent is a required field"));
        }

        return errors.isEmpty();
    }

    private boolean hasValidIp(Device device) {
        return StringUtils.isNotEmpty(device.getIp()) || StringUtils.isNotEmpty(device.getIpv6());
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(httpCall.getRequest().getPayload(), bidResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            throw new PreBidException("Empty SeatBid");
        }
        return bidsFromResponse(bidRequest, bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        final SeatBid seatBid = bidResponse.getSeatbid().get(0);
        return seatBid.getBid().stream()
                .map(bid -> BidderBid.of(bid, getBidType(bid, bidRequest.getImp()), bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private static BidType getBidType(Bid bid, List<Imp> imps) {
        final String impId = bid.getImpid();
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                } else if (imp.getVideo() != null) {
                    return BidType.video;
                } else if (imp.getXNative() != null) {
                    return BidType.xNative;
                }
            }
        }
        throw new PreBidException(String.format("Failed to find impression %s", impId));
    }
}
