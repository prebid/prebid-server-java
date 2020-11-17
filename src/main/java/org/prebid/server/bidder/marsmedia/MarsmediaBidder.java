package org.prebid.server.bidder.marsmedia;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
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
        final String requestZone;
        final BidRequest outgoingRequest;
        try {
            requestZone = resolveRequestZone(bidRequest.getImp().get(0));
            outgoingRequest = createOutgoingRequest(bidRequest);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final String uri = endpointUrl + "&zone=" + requestZone;
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

    private String resolveRequestZone(Imp firstImp) {
        final ExtImpMarsmedia extImpMarsmedia;
        try {
            extImpMarsmedia = mapper.mapper().convertValue(firstImp.getExt(), MARSMEDIA_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }

        final String zoneId = extImpMarsmedia.getZone();
        if (StringUtils.isBlank(zoneId)) {
            throw new PreBidException("Zone is empty");
        }

        return zoneId;
    }

    private static BidRequest createOutgoingRequest(BidRequest bidRequest) {
        final List<Imp> validImps = new ArrayList<>();

        final List<Imp> requestImps = bidRequest.getImp();
        boolean shouldUpdateImps = false;
        for (Imp imp : requestImps) {
            final Banner banner = imp.getBanner();
            if (banner != null) {
                final boolean hasFormats = CollectionUtils.isNotEmpty(banner.getFormat());
                //a shortcut to avoid cases when the call bidRequest.toBuilder() can be redundant as there are
                // no changes to be made
                if (!shouldUpdateImps) {
                    shouldUpdateImps = hasFormats;
                }

                validImps.add(checkBannerImp(banner, imp, hasFormats));
            } else if (imp.getVideo() != null) {
                validImps.add(imp);
            }
        }

        if (validImps.isEmpty()) {
            throw new PreBidException("No valid impression in the bid request");
        }

        return shouldUpdateImps || !Objects.equals(bidRequest.getAt(), 1)
                ? bidRequest.toBuilder().imp(validImps).at(1).build()
                : bidRequest;
    }

    private static Imp checkBannerImp(Banner banner, Imp imp, boolean hasFormats) {
        if (hasFormats) {
            final Format firstFormat = banner.getFormat().get(0);
            final Banner modifiedBanner = banner.toBuilder()
                    .w(firstFormat.getW())
                    .h(firstFormat.getH())
                    .build();

            return imp.toBuilder().banner(modifiedBanner).build();
        }

        if (banner.getW() != null && banner.getH() != null) {
            return imp;
        }

        throw new PreBidException("No valid banner format in the bid request");
    }

    private static MultiMap resolveHeaders(Device device) {
        final MultiMap headers = HttpUtil.headers()
                .add(HttpUtil.X_OPENRTB_VERSION_HEADER, "2.5");

        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.ACCEPT_LANGUAGE_HEADER, device.getLanguage());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIpv6());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.DNT_HEADER, Objects.toString(device.getDnt(), null));
        }
        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        final HttpResponse response = httpCall.getResponse();
        if (response.getStatusCode() == HttpResponseStatus.NO_CONTENT.code()) {
            return Result.empty();
        }

        try {
            final BidResponse bidResponse = mapper.decodeValue(response.getBody(), BidResponse.class);
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
        return seatbid.get(0).getBid().stream()
                .filter(Objects::nonNull)
                .map(bid -> BidderBid.of(bid, getBidType(bid.getImpid(), imps), currency))
                .collect(Collectors.toList());
    }

    private static BidType getBidType(String impid, List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getId().equals(impid)) {
                if (imp.getVideo() != null) {
                    return BidType.video;
                }
                return BidType.banner;
            }
        }
        return BidType.banner;
    }
}
