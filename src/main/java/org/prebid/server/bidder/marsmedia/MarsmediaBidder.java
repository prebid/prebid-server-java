package org.prebid.server.bidder.marsmedia;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
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
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.marsmedia.ExtImpMarsmedia;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Marsmedia {@link Bidder} implementation.
 */
public class MarsmediaBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpMarsmedia>> MARSMEDIA_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpMarsmedia>>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public MarsmediaBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final String firstImpZone;
        final BidRequest outgoingRequest;
        try {
            firstImpZone = resolveExtZone(bidRequest.getImp().get(0));
            outgoingRequest = createRequest(bidRequest);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final String uri = String.format("%s%s%s", endpointUrl, "&zone=", firstImpZone);
        final MultiMap headers = resolveHeaders(bidRequest.getDevice());

        final String body = mapper.encode(outgoingRequest);

        return Result.withValue(HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(uri)
                .headers(headers)
                .body(body)
                .payload(outgoingRequest)
                .build());
    }

    private String resolveExtZone(Imp imp) {
        final ExtImpMarsmedia extImpMarsmedia;
        try {
            extImpMarsmedia = mapper.mapper().convertValue(imp.getExt(), MARSMEDIA_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException("ext.bidder not provided");
        }

        final String zoneId = extImpMarsmedia.getZone();
        if (StringUtils.isBlank(zoneId)) {
            throw new PreBidException("Zone is empty");
        }

        return zoneId;
    }

    private static BidRequest createRequest(BidRequest request) {
        final List<Imp> validImps = new ArrayList<>();
        for (Imp imp : request.getImp()) {
            final Banner banner = imp.getBanner();
            if (banner != null) {
                if (CollectionUtils.isNotEmpty(banner.getFormat())) {
                    validImps.add(imp.toBuilder().banner(updateBanner(banner)).build());
                } else if (banner.getW() != null && banner.getH() != null) {
                    validImps.add(imp);
                } else {
                    throw new PreBidException("No valid banner format in the bid request");
                }
            } else if (imp.getVideo() != null) {
                validImps.add(imp);
            }
        }

        if (validImps.isEmpty()) {
            throw new PreBidException("No valid impression in the bid request");
        }

        return request.toBuilder().at(1).imp(validImps).build();
    }

    private static Banner updateBanner(Banner banner) {
        final Format firstFormat = banner.getFormat().get(0);
        return banner.toBuilder()
                .w(ObjectUtils.defaultIfNull(firstFormat.getW(), 0))
                .h(ObjectUtils.defaultIfNull(firstFormat.getH(), 0))
                .build();
    }

    private static MultiMap resolveHeaders(Device device) {
        final MultiMap headers = HttpUtil.headers()
                .add(HttpUtil.X_OPENRTB_VERSION_HEADER, "2.5");

        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.ACCEPT_LANGUAGE_HEADER, device.getLanguage());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.DNT_HEADER, Objects.toString(device.getDnt(), null));
        }
        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse, httpCall.getRequest().getPayload()), Collections.emptyList());
        } catch (DecodeException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse, BidRequest bidRequest) {
        return bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())
                ? Collections.emptyList()
                : bidsFromResponse(bidResponse.getSeatbid(), bidRequest.getImp(), bidResponse.getCur());
    }

    private static List<BidderBid> bidsFromResponse(List<SeatBid> seatbid, List<Imp> imps, String currency) {
        final SeatBid firstSeatBid = seatbid.get(0);
        return firstSeatBid != null ? firstSeatBid.getBid().stream()
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, getBidType(bid.getImpid(), imps), currency))
                .collect(Collectors.toList())
                : Collections.emptyList();
    }

    private static BidType getBidType(String impid, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impid)) {
                return imp.getVideo() != null ? BidType.video : BidType.banner;
            }
        }
        return BidType.banner;
    }
}
