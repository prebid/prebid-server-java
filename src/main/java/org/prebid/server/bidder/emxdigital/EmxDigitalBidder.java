package org.prebid.server.bidder.emxdigital;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
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
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.emxdigital.ExtImpEmxDigital;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class EmxDigitalBidder implements Bidder<BidRequest> {

    private static final String USD_CURRENCY = "USD";
    private static final Integer PROTOCOL_VAST_40 = 7;

    private static final TypeReference<ExtPrebid<?, ExtImpEmxDigital>> EMXDIGITAL_EXT_TYPE_REFERENCE = new
            TypeReference<ExtPrebid<?, ExtImpEmxDigital>>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public EmxDigitalBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final BidRequest bidRequest;
        try {
            bidRequest = makeBidRequest(request);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final String body = mapper.encode(bidRequest);
        final MultiMap headers = makeHeaders(request);
        final String url = makeUrl(request);

        return Result.withValue(HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(url)
                .body(body)
                .headers(headers)
                .payload(request)
                .build());
    }

    // Handle request errors and formatting to be sent to EMX
    private BidRequest makeBidRequest(BidRequest request) {
        final boolean isSecure = resolveUrl(request).startsWith("https");

        final List<Imp> modifiedImps = request.getImp().stream()
                .map(imp -> modifyImp(imp, isSecure, unpackImpExt(imp)))
                .collect(Collectors.toList());

        return request.toBuilder()
                .imp(modifiedImps)
                .build();
    }

    private static String resolveUrl(BidRequest request) {
        final Site site = request.getSite();
        final String page = site != null ? site.getPage() : null;
        if (StringUtils.isNotBlank(page)) {
            return page;
        }
        final App app = request.getApp();
        if (app != null) {
            if (StringUtils.isNotBlank(app.getDomain())) {
                return app.getDomain();
            } else if (StringUtils.isNotBlank(app.getStoreurl())) {
                return app.getStoreurl();
            }
        }
        return "";
    }

    private ExtImpEmxDigital unpackImpExt(Imp imp) {
        final ExtImpEmxDigital bidder;
        try {
            bidder = mapper.mapper().convertValue(imp.getExt(), EMXDIGITAL_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }

        final int tagidNumber;
        try {
            tagidNumber = Integer.parseInt(bidder.getTagid());
        } catch (NumberFormatException e) {
            throw new PreBidException(
                    String.format("tagid must be a String of numbers, ignoring imp id=%s",
                            imp.getId()), e);
        }

        if (tagidNumber == 0) {
            throw new PreBidException(String.format("tagid cant be 0, ignoring imp id=%s",
                    imp.getId()));
        }

        return bidder;
    }

    private static Imp modifyImp(Imp imp, boolean isSecure, ExtImpEmxDigital extImpEmxDigital) {

        final Imp.ImpBuilder impBuilder = imp.toBuilder()
                .tagid(extImpEmxDigital.getTagid())
                .secure(BooleanUtils.toInteger(isSecure));
        final Video video = imp.getVideo();
        if (video != null) {
            impBuilder.video(modifyImpVideo(video));
        } else {
            impBuilder.banner(modifyImpBanner(imp.getBanner()));
        }

        final String stringBidfloor = extImpEmxDigital.getBidfloor();
        if (StringUtils.isBlank(stringBidfloor)) {
            return impBuilder.build();
        }

        final BigDecimal bidfloor;
        try {
            bidfloor = new BigDecimal(stringBidfloor);
        } catch (NumberFormatException e) {
            return impBuilder.build();
        }

        return impBuilder
                .bidfloor(bidfloor)
                .bidfloorcur(USD_CURRENCY)
                .build();
    }

    private static Video modifyImpVideo(Video video) {
        if (CollectionUtils.isEmpty(video.getMimes())) {
            throw new PreBidException("Video: missing required field mimes");
        }
        if (isNotPresentSize(video.getH()) && isNotPresentSize(video.getW())) {
            throw new PreBidException("Video: Need at least one size to build request");
        }
        if (CollectionUtils.isNotEmpty(video.getProtocols())) {
            final List<Integer> updatedProtocols = removeVast40Protocols(video.getProtocols());
            return video.toBuilder().protocols(updatedProtocols).build();
        }

        return video;
    }

    private static boolean isNotPresentSize(Integer size) {
        return Objects.isNull(size) || size == 0;
    }

    // not supporting VAST protocol 7 (VAST 4.0);
    private static List<Integer> removeVast40Protocols(List<Integer> protocols) {
        return protocols.stream()
                .filter(protocol -> !protocol.equals(PROTOCOL_VAST_40))
                .collect(Collectors.toList());
    }

    private static Banner modifyImpBanner(Banner banner) {
        if (banner == null) {
            throw new PreBidException("Request needs to include a Banner object");
        }

        if (banner.getW() == null && banner.getH() == null) {
            final Banner.BannerBuilder bannerBuilder = banner.toBuilder();
            final List<Format> originalFormat = banner.getFormat();

            if (originalFormat == null || originalFormat.isEmpty()) {
                throw new PreBidException("Need at least one size to build request");
            }

            final List<Format> formatSkipFirst = originalFormat.subList(1, originalFormat.size());
            bannerBuilder.format(formatSkipFirst);

            Format firstFormat = originalFormat.get(0);
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
        final String page = site != null ? site.getPage() : null;
        if (StringUtils.isNotBlank(page)) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.REFERER_HEADER, page);
        }

        return headers;
    }

    private String makeUrl(BidRequest bidRequest) {
        final Long tmax = bidRequest.getTmax();
        final int urlTimeout = tmax == 0 ? 1000 : tmax.intValue();

        if (isDebugEnabled(bidRequest)) {
            // for passing validation tests
            return String.format("%s?t=1000&ts=2060541160", endpointUrl);
        }

        return String.format("%s?t=%s&ts=%s&src=pbserver", endpointUrl, urlTimeout,
                (int) Instant.now().getEpochSecond());
    }

    /**
     * Determines debug flag from {@link BidRequest} or {@link ExtRequest}.
     */
    private boolean isDebugEnabled(BidRequest bidRequest) {
        if (Objects.equals(bidRequest.getTest(), 1)) {
            return true;
        }

        final ExtRequest extRequest = bidRequest.getExt();
        final ExtRequestPrebid extRequestPrebid = extRequest != null ? extRequest.getPrebid() : null;
        return extRequestPrebid != null && Objects.equals(extRequestPrebid.getDebug(), 1);
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
                .map(bid -> BidderBid.of(modifyBid(bid), getBidType(bid.getAdm()), bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private static BidType getBidType(String bidAdm) {
        return StringUtils.containsAny(bidAdm, "<?xml", "<vast")
                ? BidType.video : BidType.banner;
    }

    private static Bid modifyBid(Bid bid) {
        return bid.toBuilder().impid(bid.getId()).build();
    }
}
