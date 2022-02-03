package org.prebid.server.bidder.vidoomy;

import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
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
import org.prebid.server.util.ObjectUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class VidoomyBidder implements Bidder<BidRequest> {

    private static final String OPENRTB_VERSION = "2.5";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public VidoomyBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<HttpRequest<BidRequest>> requests = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        for (Imp imp : request.getImp()) {
            try {
                validateImpBanner(imp.getBanner());
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
                continue;
            }
            requests.add(createRequest(request, imp));
        }

        return Result.of(requests, errors);
    }

    private static void validateImpBanner(Banner banner) {
        if (banner.getW() != null && banner.getH() != null) {
            if (banner.getW() == 0 || banner.getH() == 0) {
                throw new PreBidException(
                        String.format("invalid sizes provided for Banner %s x %s", banner.getW(), banner.getH()));
            }
        }

        if (banner.getFormat().size() == 0) {
            throw new PreBidException(
                    String.format("no sizes provided for Banner %s", banner.getFormat()));
        }
    }

    private HttpRequest<BidRequest> createRequest(BidRequest bidRequest, Imp imp) {
        final BidRequest outgoingRequest = bidRequest.toBuilder()
                .imp(Collections.singletonList(imp))
                .build();

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(getHeaders(bidRequest.getDevice()))
                .payload(outgoingRequest)
                .body(mapper.encodeToBytes(outgoingRequest))
                .build();
    }

    private static MultiMap getHeaders(Device device) {
        final MultiMap headers = HttpUtil.headers();

        headers.add(HttpUtil.X_OPENRTB_VERSION_HEADER, OPENRTB_VERSION);
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER,
                ObjectUtil.getIfNotNull(device, Device::getUa));
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER,
                ObjectUtil.getIfNotNull(device, Device::getIpv6));
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER,
                ObjectUtil.getIfNotNull(device, Device::getIp));

        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse, bidRequest));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse, BidRequest bidRequest) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidResponse, bidRequest);
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse, BidRequest bidRequest) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, resolvedBidType(bid.getImpid(), bidRequest.getImp()),
                        bidResponse.getCur()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static BidType resolvedBidType(String impid, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impid)) {
                if (imp.getVideo() != null) {
                    return BidType.video;
                }
                if (imp.getBanner() != null) {
                    return BidType.banner;
                }
            }
        }
        throw new PreBidException(String.format("unknown ad unit code '%s'", impid));
    }
}
