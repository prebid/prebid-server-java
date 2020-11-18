package org.prebid.server.bidder.beintoo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
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
import org.prebid.server.proto.openrtb.ext.request.beintoo.ExtImpBeintoo;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class BeintooBidder implements Bidder<BidRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpBeintoo>> BEINTOO_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpBeintoo>>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public BeintooBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final BidRequest updatedBidRequest;
        try {
            updatedBidRequest = updateBidRequest(request);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final String body = mapper.encode(updatedBidRequest);
        final MultiMap headers = makeHeaders(request);

        return Result.of(Collections.singletonList(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(endpointUrl)
                        .body(body)
                        .headers(headers)
                        .payload(request)
                        .build()), Collections.emptyList());
    }

    private BidRequest updateBidRequest(BidRequest request) {
        final boolean isSecure = isSecure(request.getSite());

        final List<Imp> modifiedImps = request.getImp().stream()
                .map(imp -> modifyImp(imp, isSecure, parseAndValidateImpExt(imp)))
                .collect(Collectors.toList());

        return request.toBuilder()
                .imp(modifiedImps)
                .build();
    }

    private static boolean isSecure(Site site) {
        return site != null && StringUtils.isNotBlank(site.getPage()) && site.getPage().startsWith("https");
    }

    private ExtImpBeintoo parseAndValidateImpExt(Imp imp) {
        final ExtImpBeintoo extImpBeintoo;
        try {
            extImpBeintoo = mapper.mapper().convertValue(imp.getExt(), BEINTOO_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }

        final int tagidNumber;
        final String tagId = extImpBeintoo.getTagId();
        if (StringUtils.isNumeric(tagId)) {
            tagidNumber = Integer.parseInt(tagId);
        } else {
            throw new PreBidException(String
                    .format("tagid must be a String of numbers, ignoring imp id=%s", imp.getId()));
        }

        if (tagidNumber == 0) {
            throw new PreBidException(String.format("tagid cant be 0, ignoring imp id=%s",
                    imp.getId()));
        }

        return extImpBeintoo;
    }

    private static Imp modifyImp(Imp imp, boolean isSecure, ExtImpBeintoo extImpBeintoo) {
        final Banner banner = modifyImpBanner(imp.getBanner());

        final Imp.ImpBuilder impBuilder = imp.toBuilder()
                .tagid(extImpBeintoo.getTagId())
                .secure(BooleanUtils.toInteger(isSecure))
                .banner(banner)
                .ext(null);

        final String stringBidfloor = extImpBeintoo.getBidFloor();
        final BigDecimal bidfloor = StringUtils.isBlank(stringBidfloor) ? null : new BigDecimal(stringBidfloor);
        return (bidfloor != null ? bidfloor.compareTo(BigDecimal.ZERO) : 0) > 0
                ? impBuilder.bidfloor(bidfloor).build()
                : impBuilder.build();
    }

    private static Banner modifyImpBanner(Banner banner) {
        if (banner == null) {
            throw new PreBidException("Request needs to include a Banner object");
        }

        if (banner.getW() == null && banner.getH() == null) {
            final Banner.BannerBuilder bannerBuilder = banner.toBuilder();
            final List<Format> originalFormat = banner.getFormat();

            if (CollectionUtils.isEmpty(originalFormat)) {
                throw new PreBidException("Need at least one size to build request");
            }

            final List<Format> formatSkipFirst = originalFormat.subList(1, originalFormat.size());
            bannerBuilder.format(formatSkipFirst);

            final Format firstFormat = originalFormat.get(0);
            bannerBuilder.w(firstFormat.getW());
            bannerBuilder.h(firstFormat.getH());

            return bannerBuilder.build();
        }

        return banner;
    }

    private static MultiMap makeHeaders(BidRequest request) {
        final MultiMap headers = HttpUtil.headers();

        final Device device = request.getDevice();
        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.ACCEPT_LANGUAGE_HEADER, device.getLanguage());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.DNT_HEADER, Objects.toString(device.getDnt(), null));
        }

        final Site site = request.getSite();
        if (site != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.REFERER_HEADER, site.getPage());
        }

        return headers;
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

    private static List<BidderBid> extractBids(BidResponse bidResponse) {
        return bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())
                ? Collections.emptyList()
                : bidsFromResponse(bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> bid.toBuilder().impid(bid.getId()).build())
                .map(bid -> BidderBid.of(bid, BidType.banner, bidResponse.getCur()))
                .collect(Collectors.toList());
    }
}
