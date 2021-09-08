package org.prebid.server.bidder.gamoshi;

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
import org.prebid.server.proto.openrtb.ext.request.gamoshi.ExtImpGamoshi;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Gamoshi {@link Bidder} implementation.
 */
public class GamoshiBidder implements Bidder<BidRequest> {

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public GamoshiBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> validImps = new ArrayList<>();
        for (Imp imp : request.getImp()) {
            if (imp.getBanner() == null && imp.getVideo() == null) {
                errors.add(BidderError.badInput(
                        String.format("Gamoshi only supports banner and video media types. Ignoring imp id=%s",
                                imp.getId())));
                continue;
            }
            validImps.add(processImp(imp));
        }

        if (validImps.isEmpty()) {
            errors.add(BidderError.badInput("No valid impressions in the bid request"));
            return Result.of(Collections.emptyList(), errors);
        }

        final ExtImpGamoshi firstImpExt;
        try {
            firstImpExt = parseAndValidateImpExt(validImps.get(0));
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final BidRequest outgoingRequest = request.toBuilder().imp(validImps).build();
        final String body = mapper.encode(outgoingRequest);

        final String requestUrl = endpointUrl + "/r/" + firstImpExt.getSupplyPartnerId() + "/bidr?bidder=prebid-server";
        final MultiMap headers = resolveHeaders(request.getDevice());

        return Result.of(Collections.singletonList(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(requestUrl)
                        .headers(headers)
                        .payload(outgoingRequest)
                        .body(body)
                        .build()),
                errors);
    }

    private static Imp processImp(Imp imp) {
        final Banner banner = imp.getBanner();
        if (banner != null && banner.getH() == null && banner.getW() == null
                && CollectionUtils.isNotEmpty(banner.getFormat())) {
            final Format firstFormat = banner.getFormat().get(0);
            final Banner modifiedBanner = banner.toBuilder()
                    .h(firstFormat.getH())
                    .w(firstFormat.getW())
                    .build();
            return imp.toBuilder().banner(modifiedBanner).build();
        }
        return imp;
    }

    private ExtImpGamoshi parseAndValidateImpExt(Imp imp) {
        final ExtImpGamoshi extImpGamoshi;
        try {
            extImpGamoshi = mapper.mapper().convertValue(imp.getExt().get("bidder"), ExtImpGamoshi.class);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }

        if (StringUtils.isBlank(extImpGamoshi.getSupplyPartnerId())) {
            throw new PreBidException("supplyPartnerId is empty");
        }

        return extImpGamoshi;
    }

    private static MultiMap resolveHeaders(Device device) {
        final MultiMap headers = HttpUtil.headers()
                .add(HttpUtil.X_OPENRTB_VERSION_HEADER, "2.4");

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
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidRequest, bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        final Map<String, BidType> impIdToBidType = bidRequest.getImp().stream()
                .collect(Collectors.toMap(Imp::getId, GamoshiBidder::getBidType));

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, impIdToBidType.getOrDefault(bid.getImpid(), BidType.banner),
                        bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private static BidType getBidType(Imp imp) {
        return imp.getVideo() != null ? BidType.video : BidType.banner;
    }
}
