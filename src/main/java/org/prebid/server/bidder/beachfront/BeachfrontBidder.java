package org.prebid.server.bidder.beachfront;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.beachfront.model.BeachfrontBannerRequest;
import org.prebid.server.bidder.beachfront.model.BeachfrontResponseSlot;
import org.prebid.server.bidder.beachfront.model.BeachfrontSize;
import org.prebid.server.bidder.beachfront.model.BeachfrontSlot;
import org.prebid.server.bidder.beachfront.model.BeachfrontVideoRequest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.beachfront.ExtImpBeachfront;
import org.prebid.server.proto.openrtb.ext.request.beachfront.ExtImpBeachfrontAppIds;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Beachfront {@link Bidder} implementation.
 */
public class BeachfrontBidder implements Bidder<Void> {

    private static final String DEFAULT_BID_CURRENCY = "USD";
    private static final String NURL_VIDEO_TYPE = "nurl";
    private static final String ADM_VIDEO_TYPE = "adm";
    private static final String BEACHFRONT_NAME = "BF_PREBID_S2S";
    private static final String BEACHFRONT_VERSION = "0.9.2";
    private static final String NURL_VIDEO_ENDPOINT_SUFFIX = "&prebidserver";
    private static final String FAKE_IP = "255.255.255.255";

    private static final BigDecimal MIN_BID_FLOOR = BigDecimal.valueOf(0.01);
    private static final int DEFAULT_VIDEO_WIDTH = 300;
    private static final int DEFAULT_VIDEO_HEIGHT = 250;

    private static final TypeReference<ExtPrebid<?, ExtImpBeachfront>> BEACHFRONT_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpBeachfront>>() {
            };

    private final String bannerEndpointUrl;
    private final String videoEndpointUrl;
    private final JacksonMapper mapper;

    public BeachfrontBidder(String bannerEndpointUrl, String videoEndpointUrl, JacksonMapper mapper) {
        this.bannerEndpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(bannerEndpointUrl));
        this.videoEndpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(videoEndpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<Void>>> makeHttpRequests(BidRequest bidRequest) {
        final List<Imp> bannerImps = new ArrayList<>();
        final List<Imp> videoImps = new ArrayList<>();
        for (Imp imp : bidRequest.getImp()) {
            final Banner banner = imp.getBanner();
            if (checkFormats(banner)) {
                bannerImps.add(imp);
            }
            if (imp.getVideo() != null) {
                videoImps.add(imp);
            }
        }
        if (bannerImps.isEmpty() && videoImps.isEmpty()) {
            return Result.withError(BidderError.badInput("no valid impressions were found in the request"));
        }

        final List<BidderError> errors = new ArrayList<>();
        final BeachfrontBannerRequest bannerRequest = getBannerRequest(bidRequest, bannerImps, errors);
        final List<BeachfrontVideoRequest> videoRequests = getVideoRequests(bidRequest, videoImps, errors);

        final MultiMap headers = HttpUtil.headers();
        final Device device = bidRequest.getDevice();
        if (device != null) {
            addDeviceHeaders(headers, device);
        }

        final List<HttpRequest<Void>> requests = new ArrayList<>();
        if (bannerRequest != null) {
            requests.add(HttpRequest.<Void>builder()
                    .method(HttpMethod.POST)
                    .uri(bannerEndpointUrl)
                    .body(mapper.encode(bannerRequest))
                    .headers(headers)
                    .build());
        }

        final MultiMap videoHeaders = MultiMap.caseInsensitiveMultiMap().addAll(headers);

        final User user = bidRequest.getUser();
        final String buyerUid = user != null ? user.getBuyeruid() : null;
        if (!videoRequests.isEmpty() && StringUtils.isNotBlank(buyerUid)) {
            videoHeaders.add("Cookie", "__io_cid=" + buyerUid);
        }

        videoRequests.stream()
                .map(videoRequest -> HttpRequest.<Void>builder()
                        .method(HttpMethod.POST)
                        .uri(resolveVideoUri(videoRequest.getAppId(), videoRequest.getIsPrebid()))
                        .body(mapper.encode(videoRequest))
                        .headers(videoHeaders)
                        .build())
                .forEach(requests::add);

        return Result.of(requests, errors);
    }

    private String resolveVideoUri(String appId, Boolean isPrebid) {
        final String videoWithId = videoEndpointUrl + appId;
        return BooleanUtils.toBoolean(isPrebid)
                ? videoWithId + NURL_VIDEO_ENDPOINT_SUFFIX
                : videoWithId;
    }

    private static boolean checkFormats(Banner banner) {
        final List<Format> formats = banner != null ? banner.getFormat() : null;
        final Format firstFormat = CollectionUtils.isNotEmpty(formats) ? formats.get(0) : null;
        final boolean isHeightNonZero = firstFormat != null && !Objects.equals(firstFormat.getH(), 0);
        final boolean isWidthNonZero = firstFormat != null && !Objects.equals(firstFormat.getW(), 0);
        return isHeightNonZero && isWidthNonZero;
    }

    private BeachfrontBannerRequest getBannerRequest(BidRequest bidRequest, List<Imp> bannerImps,
                                                     List<BidderError> errors) {
        final List<BeachfrontSlot> slots = new ArrayList<>();

        for (Imp imp : bannerImps) {
            try {
                final ExtImpBeachfront extImpBeachfront = parseImpExt(imp);
                final String appId = getAppId(extImpBeachfront, true);

                slots.add(BeachfrontSlot.of(imp.getId(), appId, checkBidFloor(extImpBeachfront.getBidfloor()),
                        makeBeachfrontSizes(imp.getBanner())));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }
        if (slots.isEmpty()) {
            return null;
        }

        final BeachfrontBannerRequest.BeachfrontBannerRequestBuilder requestBuilder = BeachfrontBannerRequest.builder()
                .adapterName(BEACHFRONT_NAME)
                .adapterVersion(BEACHFRONT_VERSION)
                .requestId(bidRequest.getId())
                .real204(true)
                .slots(slots);

        final User user = bidRequest.getUser();
        if (user != null) {
            requestBuilder.user(makeUser(user));
        }

        final Device device = bidRequest.getDevice();
        if (device != null) {
            populateDeviceFields(requestBuilder, device);
        }

        final Site site = bidRequest.getSite();
        final Integer firstImpSecure = bannerImps.get(0).getSecure();
        if (site != null) {
            final String page = site.getPage();

            requestBuilder.page(page);
            requestBuilder.domain(StringUtils.isBlank(site.getDomain())
                    ? HttpUtil.getHostFromUrl(page) : site.getDomain());
            requestBuilder.isMobile(0);
            requestBuilder.secure(firstImpSecure != null ? firstImpSecure : getSecure(page));
        } else {
            final App app = bidRequest.getApp();
            final String bundle = app.getBundle();

            requestBuilder.page(bundle);
            requestBuilder.domain(app.getDomain());
            requestBuilder.isMobile(1);
            requestBuilder.secure(firstImpSecure != null ? firstImpSecure : getSecure(bundle));
        }

        return requestBuilder.build();
    }

    private ExtImpBeachfront parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), BEACHFRONT_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(String.format(
                    "ignoring imp id=%s, error while decoding extImpBeachfront, err: %s", imp.getId(), e.getMessage()));
        }
    }

    private static String getAppId(ExtImpBeachfront extImpBeachfront, boolean isBanner) {
        final String appId = extImpBeachfront.getAppId();
        if (StringUtils.isNotBlank(appId)) {
            return appId;
        }

        final ExtImpBeachfrontAppIds appIds = extImpBeachfront.getAppIds();
        final String bannerAppId = appIds != null ? appIds.getBanner() : null;
        if (isBanner && StringUtils.isNotBlank(bannerAppId)) {
            return bannerAppId;
        }

        final String videoAppId = appIds != null ? appIds.getVideo() : null;
        if (StringUtils.isNotBlank(videoAppId)) {
            return videoAppId;
        }
        throw new PreBidException("unable to determine the appId(s) from the supplied extension");
    }

    private static BigDecimal checkBidFloor(BigDecimal bidFloor) {
        return bidFloor != null && bidFloor.compareTo(MIN_BID_FLOOR) > 0 ? bidFloor : BigDecimal.ZERO;
    }

    /**
     * Creates {@link BeachfrontSize} by copying from {@link Format}s.
     */
    private static List<BeachfrontSize> makeBeachfrontSizes(Banner banner) {
        final List<BeachfrontSize> beachfrontSizes = new ArrayList<>();
        if (banner.getFormat() != null) {
            for (final Format format : banner.getFormat()) {
                beachfrontSizes.add(BeachfrontSize.of(format.getW(), format.getH()));
            }
        }
        return beachfrontSizes;
    }

    /**
     * Creates {@link BeachfrontVideoRequest} user.
     */
    private static User makeUser(User user) {
        final String userId = user.getId();
        final String buyerId = user.getBuyeruid();
        return User.builder()
                // Exchange-specific ID for the user. At least one of id or
                // buyeruid is recommended.
                .id(StringUtils.isNotEmpty(userId) ? userId : null)
                // Buyer-specific ID for the user as mapped by the exchange for
                // the buyer. At least one of buyeruid or id is recommended.
                .buyeruid(StringUtils.isNotEmpty(buyerId) ? buyerId : null)
                .build();
    }

    /**
     * Populates {@link BeachfrontBannerRequest} with fields from {@link Device} if it is not null.
     */
    private static void populateDeviceFields(BeachfrontBannerRequest.BeachfrontBannerRequestBuilder builder,
                                             Device device) {
        builder.ip(device.getIp());
        builder.deviceModel(device.getModel());
        builder.deviceOs(device.getOs());
        if (device.getDnt() != null) {
            builder.dnt(device.getDnt());
        }
        if (StringUtils.isNotEmpty(device.getUa())) {
            builder.ua(device.getUa());
        }
    }

    private static int getSecure(String page) {
        return StringUtils.contains(page, "https") ? 1 : 0;
    }

    private List<BeachfrontVideoRequest> getVideoRequests(BidRequest bidRequest, List<Imp> videoImps,
                                                          List<BidderError> errors) {
        final List<BeachfrontVideoRequest> videoRequests = new ArrayList<>();
        for (Imp imp : videoImps) {
            final ExtImpBeachfront extImpBeachfront;
            final String appId;

            try {
                extImpBeachfront = parseImpExt(imp);
                appId = getAppId(extImpBeachfront, false);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
                continue;
            }

            final String videoResponseType = extImpBeachfront.getVideoResponseType();
            final BeachfrontVideoRequest.BeachfrontVideoRequestBuilder requestBuilder = BeachfrontVideoRequest.builder()
                    .appId(appId);
            final String responseType;

            if (videoResponseType != null && videoResponseType.equals(NURL_VIDEO_TYPE)) {
                requestBuilder.isPrebid(true);
                responseType = NURL_VIDEO_TYPE;
            } else {
                responseType = ADM_VIDEO_TYPE;
            }

            requestBuilder.videoResponseType(responseType);

            final BidRequest.BidRequestBuilder bidRequestBuilder = bidRequest.toBuilder();
            int secure = 0;
            final Site site = bidRequest.getSite();
            if (site != null && StringUtils.isBlank(site.getDomain()) && StringUtils.isNotBlank(site.getPage())) {
                bidRequestBuilder.site(site.toBuilder().domain(HttpUtil.getHostFromUrl(site.getPage())).build());

                secure = getSecure(site.getPage());
            }

            final Device device = bidRequest.getDevice();
            if (device != null) {
                final Device.DeviceBuilder deviceBuilder = device.toBuilder();
                final Integer devicetype = device.getDevicetype();
                if (devicetype == null || devicetype == 0) {
                    deviceBuilder.devicetype(bidRequest.getSite() != null ? 2 : 1);
                }
                if (StringUtils.isBlank(device.getIp()) && responseType.equals(ADM_VIDEO_TYPE)) {
                    deviceBuilder.ip(FAKE_IP);
                }

                bidRequestBuilder.device(deviceBuilder.build());
            }

            final App app = bidRequest.getApp();
            if (app != null && StringUtils.isBlank(app.getDomain()) && StringUtils.isNotBlank(app.getBundle())) {
                final String trimmedBundle = StringUtils.removeStart(app.getBundle(), "_");
                final String[] split = StringUtils.removeEnd(trimmedBundle, "_").split("\\.");

                if (split.length > 1) {
                    bidRequestBuilder.app(app.toBuilder().domain(String.format("%s.%s", split[1], split[0])).build());
                }
            }

            final Imp.ImpBuilder impBuilder = imp.toBuilder()
                    .banner(null)
                    .ext(null)
                    .secure(secure)
                    .bidfloor(checkBidFloor(extImpBeachfront.getBidfloor()));

            final Video video = imp.getVideo();
            final Integer videoHeight = video.getH();
            final Integer videoWidth = video.getW();
            if ((videoHeight == null || videoHeight == 0) && (videoWidth == null || videoWidth == 0)) {
                impBuilder.video(video.toBuilder().h(DEFAULT_VIDEO_HEIGHT).w(DEFAULT_VIDEO_WIDTH).build());
            }

            bidRequestBuilder.imp(Collections.singletonList(impBuilder.build()));

            if (CollectionUtils.isEmpty(bidRequest.getCur())) {
                bidRequestBuilder.cur(Collections.singletonList(DEFAULT_BID_CURRENCY));
            }

            videoRequests.add(requestBuilder.request(bidRequestBuilder.build()).build());
        }

        return videoRequests;
    }

    private static void addDeviceHeaders(MultiMap headers, Device device) {
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, device.getUa());
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.ACCEPT_LANGUAGE_HEADER, device.getLanguage());
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.DNT_HEADER, Objects.toString(device.getDnt(), null));
    }

    /**
     * Converts response to {@link List} of {@link BidderBid}s with {@link List}, depends on response type.
     */
    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<Void> httpCall, BidRequest bidRequest) {
        final String bodyString = httpCall.getResponse().getBody();
        try {
            return processVideoResponse(bodyString, httpCall.getRequest());
        } catch (DecodeException ignored) {
            try {
                return processBannerResponse(bodyString);
            } catch (PreBidException e) {
                return Result.withError(BidderError.badServerResponse(e.getMessage()));
            }
        }
    }

    /**
     * Creates response for banner response, by creating response {@link Bid} from {@link BeachfrontResponseSlot}.
     */
    private Result<List<BidderBid>> processBannerResponse(String responseBody) {
        final List<BeachfrontResponseSlot> responseSlots = makeBeachfrontResponseSlots(responseBody);

        return Result.withValues(responseSlots.stream()
                .filter(Objects::nonNull)
                .map(BeachfrontBidder::makeBidFromBeachfrontSlot)
                .map(bid -> BidderBid.of(bid, BidType.banner, DEFAULT_BID_CURRENCY))
                .collect(Collectors.toList()));
    }

    /**
     * Parses response body to list of {@link BeachfrontResponseSlot}s.
     * <p>
     * Throws {@link PreBidException} in case of failure.
     */
    private List<BeachfrontResponseSlot> makeBeachfrontResponseSlots(String responseBody) {
        try {
            return mapper.mapper().readValue(
                    responseBody,
                    mapper.mapper().getTypeFactory().constructCollectionType(List.class, BeachfrontResponseSlot.class));
        } catch (IOException e) {
            throw new PreBidException("server response failed to unmarshal "
                    + "as valid rtb. Run with request.debug = 1 for more info");
        }
    }

    /**
     * Creates {@link Bid} from {@link BeachfrontResponseSlot}.
     */
    private static Bid makeBidFromBeachfrontSlot(BeachfrontResponseSlot beachfrontResponseSlot) {
        final String slot = beachfrontResponseSlot.getSlot();
        return Bid.builder()
                .crid(beachfrontResponseSlot.getCrid())
                .impid(slot)
                .price(BigDecimal.valueOf(beachfrontResponseSlot.getPrice()))
                .id(slot + "Banner")
                .adm(beachfrontResponseSlot.getAdm())
                .h(beachfrontResponseSlot.getH())
                .w(beachfrontResponseSlot.getW())
                .build();
    }

    private Result<List<BidderBid>> processVideoResponse(String responseBody, HttpRequest httpRequest) {
        final BidResponse bidResponse = mapper.decodeValue(responseBody, BidResponse.class);
        final BeachfrontVideoRequest videoRequest = mapper.decodeValue(
                httpRequest.getBody(), BeachfrontVideoRequest.class);

        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Result.empty();
        }

        final List<Bid> bids = bidResponse.getSeatbid().get(0).getBid();
        final List<Imp> imps = videoRequest.getRequest().getImp();
        if (httpRequest.getUri().contains(NURL_VIDEO_ENDPOINT_SUFFIX)) {
            return Result.withValues(updateNurlVideoBids(bids, imps).stream()
                    .map(bid -> BidderBid.of(bid, BidType.video, bidResponse.getCur()))
                    .collect(Collectors.toList()));
        } else {
            return Result.withValues(updateVideoBids(bids).stream()
                    .map(bid -> BidderBid.of(bid, BidType.video, bidResponse.getCur()))
                    .collect(Collectors.toList()));
        }
    }

    private static List<Bid> updateNurlVideoBids(List<Bid> bids, List<Imp> imps) {
        final List<Bid> result = new ArrayList<>();
        for (int i = 0; i < bids.size(); i++) {
            Bid bid = bids.get(i);
            final Imp imp = imps.get(i);

            final String impId = imp.getId();

            bid = bid.toBuilder()
                    .crid(getCrId(bid.getNurl()))
                    .impid(impId)
                    .h(imp.getVideo().getH())
                    .w(imp.getVideo().getW())
                    .id(impId + "NurlVideo")
                    .build();
            result.add(bid);
        }
        return result;
    }

    private static List<Bid> updateVideoBids(List<Bid> bids) {
        return bids.stream()
                .map(bid -> bid.toBuilder().id(bid.getImpid() + "AdmVideo").build())
                .collect(Collectors.toList());
    }

    private static String getCrId(String nurl) {
        final String[] split = nurl.split(":");
        return split.length > 2 ? split[2] : null;
    }
}
