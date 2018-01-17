package org.rtb.vexing.adapter.facebook;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.rtb.vexing.adapter.Adapter;
import org.rtb.vexing.adapter.facebook.model.FacebookExt;
import org.rtb.vexing.adapter.facebook.model.FacebookParams;
import org.rtb.vexing.exception.PreBidException;
import org.rtb.vexing.model.AdUnitBid;
import org.rtb.vexing.model.BidResult;
import org.rtb.vexing.model.Bidder;
import org.rtb.vexing.model.BidderResult;
import org.rtb.vexing.model.MediaType;
import org.rtb.vexing.model.PreBidRequestContext;
import org.rtb.vexing.model.response.Bid;
import org.rtb.vexing.model.response.BidderDebug;
import org.rtb.vexing.model.response.BidderStatus;
import org.rtb.vexing.model.response.UsersyncInfo;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Clock;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class FacebookAdapter implements Adapter {

    private static final Logger logger = LoggerFactory.getLogger(FacebookAdapter.class);

    // UsersyncInfo and FacebookParams fields are not in snake-case
    private static final ObjectMapper DEFAULT_NAMING_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final Clock CLOCK = Clock.systemDefaultZone();

    private static final Set<MediaType> ALLOWED_MEDIA_TYPES =
            Collections.unmodifiableSet(EnumSet.of(MediaType.banner, MediaType.video));

    private static final Set<Integer> ALLOWED_BANNER_HEIGHTS =
            Collections.unmodifiableSet(new HashSet<>(Arrays.asList(50, 90, 250)));

    private static final Integer LEGACY_BANNER_WIDTH = 320;
    private static final Integer LEGACY_BANNER_HEIGHT = 50;

    private static final Random RANDOM = new Random();

    private static final String APPLICATION_JSON =
            HttpHeaderValues.APPLICATION_JSON.toString() + ";" + HttpHeaderValues.CHARSET.toString() + "=" + "utf-8";

    private final String endpointUrl;
    private final String nonSecureEndpointUrl;
    private final ObjectNode usersyncInfo;
    private final ObjectNode platformJson;
    private final HttpClient httpClient;

    public FacebookAdapter(String endpointUrl, String nonSecureEndpointUrl, String usersyncUrl, String platformId,
                           HttpClient httpClient) {
        this.endpointUrl = validateUrl(Objects.requireNonNull(endpointUrl));
        this.nonSecureEndpointUrl = validateUrl(Objects.requireNonNull(nonSecureEndpointUrl));

        usersyncInfo = createUsersyncInfo(Objects.requireNonNull(usersyncUrl));
        platformJson = createPlatformJson(Objects.requireNonNull(platformId));

        this.httpClient = Objects.requireNonNull(httpClient);
    }

    private static String validateUrl(String url) {
        try {
            return new URL(url).toString();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(String.format("URL supplied is not valid: '%s'", url), e);
        }
    }

    private static ObjectNode createUsersyncInfo(String usersyncUrl) {
        return DEFAULT_NAMING_MAPPER.valueToTree(UsersyncInfo.builder()
                .url(usersyncUrl)
                .type("redirect")
                .supportCORS(false)
                .build());
    }

    private static ObjectNode createPlatformJson(String platformId) {
        final Integer platformIdAsInt;
        try {
            platformIdAsInt = Integer.valueOf(platformId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(String.format("Platform ID is not valid number: '%s'", platformId), e);
        }
        return Json.mapper.valueToTree(FacebookExt.builder().platformid(platformIdAsInt).build());
    }

    @Override
    public Future<BidderResult> requestBids(Bidder bidder, PreBidRequestContext preBidRequestContext) {
        Objects.requireNonNull(bidder);
        Objects.requireNonNull(preBidRequestContext);

        final long bidderStarted = CLOCK.millis();

        final List<BidWithRequest> bidWithRequests;
        try {
            validateAdUnitBids(bidder.adUnitBids);

            bidWithRequests = createAdUnitBidsWithParams(bidder.adUnitBids).stream()
                    .flatMap(adUnitBidWithParams -> createBidWithRequests(adUnitBidWithParams, preBidRequestContext))
                    .collect(Collectors.toList());
        } catch (PreBidException e) {
            logger.warn("Error occurred while constructing bid requests", e);
            return Future.succeededFuture(BidderResult.builder()
                    .bidderStatus(BidderStatus.builder()
                            .error(e.getMessage())
                            .responseTimeMs(responseTime(bidderStarted))
                            .build())
                    .bids(Collections.emptyList())
                    .build());
        }

        final List<Future> requestBidFutures = bidWithRequests.stream()
                .map(bidWithRequest -> requestSingleBid(bidWithRequest.bidRequest, preBidRequestContext.timeout,
                        bidWithRequest.adUnitBid))
                .collect(Collectors.toList());

        final Future<BidderResult> bidderResultFuture = Future.future();
        CompositeFuture.join(requestBidFutures).setHandler(requestBidsResult ->
                bidderResultFuture.complete(toBidderResult(bidder, preBidRequestContext,
                        requestBidsResult.result().list(), bidderStarted)));
        return bidderResultFuture;
    }

    private static void validateAdUnitBids(List<AdUnitBid> adUnitBids) {
        adUnitBids.stream()
                .filter(adUnitBid -> adUnitBid.mediaTypes.contains(MediaType.video))
                .forEach(FacebookAdapter::validateVideoMediaType);

        adUnitBids.stream()
                .filter(adUnitBid -> adUnitBid.mediaTypes.contains(MediaType.banner))
                .forEach(FacebookAdapter::validateBannerMediaType);
    }

    private static void validateVideoMediaType(AdUnitBid adUnitBid) {
        if (adUnitBid.video == null || CollectionUtils.isEmpty(adUnitBid.video.mimes)) {
            throw new PreBidException("Invalid AdUnit: VIDEO media type with no video data");
        }
    }

    private static void validateBannerMediaType(AdUnitBid adUnitBid) {
        // if instl = 0 and type is banner, do not send non supported size
        if (Objects.equals(adUnitBid.instl, 0)
                && !ALLOWED_BANNER_HEIGHTS.contains(adUnitBid.sizes.get(0).getH())) {
            throw new PreBidException("Facebook do not support banner height other than 50, 90 and 250");
        }
    }

    private static List<AdUnitBidWithParams> createAdUnitBidsWithParams(List<AdUnitBid> adUnitBids) {
        return adUnitBids.stream()
                .map(adUnitBid -> AdUnitBidWithParams.of(adUnitBid, parseAndValidateParams(adUnitBid)))
                .collect(Collectors.toList());
    }

    private static Params parseAndValidateParams(AdUnitBid adUnitBid) {
        if (adUnitBid.params == null) {
            throw new PreBidException("Facebook params section is missing");
        }

        final FacebookParams params;
        try {
            params = DEFAULT_NAMING_MAPPER.convertValue(adUnitBid.params, FacebookParams.class);
        } catch (IllegalArgumentException e) {
            // a weird way to pass parsing exception
            throw new PreBidException(e.getMessage(), e.getCause());
        }

        if (StringUtils.isEmpty(params.placementId)) {
            throw new PreBidException("Missing placementId param");
        }

        final String[] splitted = params.placementId.split("_");
        if (splitted.length != 2) {
            throw new PreBidException(String.format("Invalid placementId param '%s'", params.placementId));
        }

        return Params.of(params.placementId, splitted[0]);
    }

    private Stream<BidWithRequest> createBidWithRequests(AdUnitBidWithParams adUnitBidWithParams,
                                                         PreBidRequestContext preBidRequestContext) {
        final List<Imp> imps = makeImps(adUnitBidWithParams, preBidRequestContext);
        validateImps(imps);

        return imps.stream()
                .map(imp -> BidWithRequest.of(adUnitBidWithParams.adUnitBid, BidRequest.builder()
                        .id(preBidRequestContext.preBidRequest.tid)
                        .at(1)
                        .tmax(preBidRequestContext.timeout)
                        .imp(Collections.singletonList(imp))
                        .app(makeApp(preBidRequestContext, adUnitBidWithParams.params))
                        .site(makeSite(preBidRequestContext, adUnitBidWithParams.params))
                        .device(makeDevice(preBidRequestContext))
                        .user(makeUser(preBidRequestContext))
                        .source(makeSource(preBidRequestContext))
                        .ext(platformJson)
                        .build()));
    }

    private static List<Imp> makeImps(AdUnitBidWithParams adUnitBidWithParams,
                                      PreBidRequestContext preBidRequestContext) {
        final AdUnitBid adUnitBid = adUnitBidWithParams.adUnitBid;

        return allowedMediaTypes(adUnitBid).stream()
                .map(mediaType -> impBuilderWithMedia(mediaType, adUnitBid)
                        .id(adUnitBid.adUnitCode)
                        .instl(adUnitBid.instl)
                        .secure(preBidRequestContext.secure)
                        .tagid(adUnitBidWithParams.params.placementId)
                        .build())
                .collect(Collectors.toList());
    }

    private static Set<MediaType> allowedMediaTypes(AdUnitBid adUnitBid) {
        return ALLOWED_MEDIA_TYPES.stream()
                .filter(adUnitBid.mediaTypes::contains)
                .collect(Collectors.toSet());
    }

    private static Imp.ImpBuilder impBuilderWithMedia(MediaType mediaType, AdUnitBid adUnitBid) {
        final Imp.ImpBuilder impBuilder = Imp.builder();

        switch (mediaType) {
            case video:
                impBuilder.video(makeVideo(adUnitBid));
                break;
            case banner:
                impBuilder.banner(makeBanner(adUnitBid));
                break;
            default:
                // unknown media type, just skip it
        }
        return impBuilder;
    }

    private static Banner makeBanner(AdUnitBid adUnitBid) {
        final Integer w = adUnitBid.sizes.get(0).getW();
        final Integer h = adUnitBid.sizes.get(0).getH();

        final Integer width;
        final Integer height;
        if (Objects.equals(adUnitBid.instl, 1)) {
            // if instl = 1 sent in, pass size (0,0) to facebook
            width = 0;
            height = 0;
        } else if (Objects.equals(w, LEGACY_BANNER_WIDTH) && Objects.equals(h, LEGACY_BANNER_HEIGHT)) {
            // do not send legacy 320x50 size to facebook, instead use 0x50
            width = 0;
            height = h;
        } else {
            width = w;
            height = h;
        }

        return Banner.builder()
                .w(width)
                .h(height)
                .format(adUnitBid.sizes)
                .topframe(adUnitBid.topframe)
                .build();
    }

    private static Video makeVideo(AdUnitBid adUnitBid) {
        return Video.builder()
                .mimes(adUnitBid.video.mimes)
                .minduration(adUnitBid.video.minduration)
                .maxduration(adUnitBid.video.maxduration)
                .w(adUnitBid.sizes.get(0).getW())
                .h(adUnitBid.sizes.get(0).getH())
                .startdelay(adUnitBid.video.startdelay)
                .playbackmethod(Collections.singletonList(adUnitBid.video.playbackMethod))
                .protocols(adUnitBid.video.protocols)
                .build();
    }

    private static void validateImps(List<Imp> imps) {
        if (imps.isEmpty()) {
            throw new PreBidException("openRTB bids need at least one Imp");
        }
    }

    private static App makeApp(PreBidRequestContext preBidRequestContext, Params params) {
        final App app = preBidRequestContext.preBidRequest.app;
        return app == null ? null : app.toBuilder()
                .publisher(Publisher.builder().id(params.pubId).build())
                .build();
    }

    private static Site makeSite(PreBidRequestContext preBidRequestContext, Params params) {
        return preBidRequestContext.preBidRequest.app != null ? null : Site.builder()
                .domain(preBidRequestContext.domain)
                .page(preBidRequestContext.referer)
                .publisher(Publisher.builder().id(params.pubId).build())
                .build();
    }

    private static Device makeDevice(PreBidRequestContext preBidRequestContext) {
        final Device device = preBidRequestContext.preBidRequest.device;

        // create a copy since device might be shared with other adapters
        final Device.DeviceBuilder deviceBuilder = device != null ? device.toBuilder() : Device.builder();

        if (preBidRequestContext.preBidRequest.app == null) {
            deviceBuilder.ua(preBidRequestContext.ua);
        }

        return deviceBuilder
                .ip(preBidRequestContext.ip)
                .build();
    }

    private User makeUser(PreBidRequestContext preBidRequestContext) {
        return preBidRequestContext.preBidRequest.app != null ? preBidRequestContext.preBidRequest.user
                : User.builder()
                .buyeruid(preBidRequestContext.uidsCookie.uidFrom(cookieFamily()))
                // id is a UID for "adnxs" (see logic in open-source implementation)
                .id(preBidRequestContext.uidsCookie.uidFrom("adnxs"))
                .build();
    }

    private static Source makeSource(PreBidRequestContext preBidRequestContext) {
        return Source.builder()
                .tid(preBidRequestContext.preBidRequest.tid)
                .fd(preBidRequestContext.preBidRequest.app == null ? 1 : null) // upstream, aka header
                .build();
    }

    private static int responseTime(long bidderStarted) {
        return Math.toIntExact(CLOCK.millis() - bidderStarted);
    }

    private Future<BidResult> requestSingleBid(BidRequest bidRequest, long timeout, AdUnitBid adUnitBid) {
        final String bidRequestBody = Json.encode(bidRequest);

        // 50% of traffic to non-secure endpoint
        final String requestUrl = RANDOM.nextBoolean() ? endpointUrl : nonSecureEndpointUrl;

        final BidderDebug.BidderDebugBuilder bidderDebugBuilder = beginBidderDebug(requestUrl, bidRequestBody);

        final Future<BidResult> future = Future.future();
        httpClient.postAbs(requestUrl, response -> handleResponse(response, adUnitBid, bidderDebugBuilder, future))
                .exceptionHandler(exception -> handleException(exception, bidderDebugBuilder, future))
                .putHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                .putHeader(HttpHeaders.ACCEPT, HttpHeaderValues.APPLICATION_JSON)
                .setTimeout(timeout)
                .end(bidRequestBody);
        return future;
    }

    private BidderDebug.BidderDebugBuilder beginBidderDebug(String requestUrl, String requestBody) {
        return BidderDebug.builder()
                .requestUri(requestUrl)
                .requestBody(requestBody);
    }

    private static BidderDebug completeBidderDebug(BidderDebug.BidderDebugBuilder bidderDebugBuilder,
                                                   int statusCode, String responseBody) {
        return bidderDebugBuilder
                .statusCode(statusCode)
                .responseBody(responseBody)
                .build();
    }

    private void handleResponse(HttpClientResponse response, AdUnitBid adUnitBid,
                                BidderDebug.BidderDebugBuilder bidderDebugBuilder, Future<BidResult> future) {
        response
                .bodyHandler(buffer -> future.complete(toBidResult(adUnitBid, bidderDebugBuilder,
                        response.statusCode(), buffer.toString())))
                .exceptionHandler(exception -> handleException(exception, bidderDebugBuilder, future));
    }

    private BidResult toBidResult(AdUnitBid adUnitBid, BidderDebug.BidderDebugBuilder bidderDebugBuilder,
                                  int statusCode, String body) {
        final BidderDebug bidderDebug = completeBidderDebug(bidderDebugBuilder, statusCode, body);

        if (statusCode != 200) {
            logger.warn("Bid response code is {0}, body: {1}", statusCode, body);
            return BidResult.error(bidderDebug, String.format("HTTP status %d; body: %s", statusCode, body));
        }

        final BidResponse bidResponse;
        try {
            bidResponse = Json.decodeValue(body, BidResponse.class);
        } catch (DecodeException e) {
            logger.warn("Error occurred while parsing bid response: {0}", e, body);
            return BidResult.error(bidderDebug, e.getMessage());
        }

        final com.iab.openrtb.response.Bid bid = bidResponse != null ? singleBidFrom(bidResponse) : null;
        if (bid == null) {
            return BidResult.empty(bidderDebug);
        }

        // validate that impId matches expected ad unit code
        return Objects.equals(bid.getImpid(), adUnitBid.adUnitCode)
                ? BidResult.success(Collections.singletonList(toBidBuilder(bid, adUnitBid)), bidderDebug)
                : BidResult.error(bidderDebug, String.format("Unknown ad unit code '%s'", bid.getImpid()));
    }

    private static com.iab.openrtb.response.Bid singleBidFrom(BidResponse bidResponse) {
        final List<SeatBid> seatBids = bidResponse.getSeatbid();
        return CollectionUtils.isNotEmpty(seatBids) && CollectionUtils.isNotEmpty(seatBids.get(0).getBid())
                ? seatBids.get(0).getBid().get(0) : null;
    }

    private static Bid.BidBuilder toBidBuilder(com.iab.openrtb.response.Bid bid, AdUnitBid adUnitBid) {
        return Bid.builder()
                .code(bid.getImpid())
                .price(bid.getPrice())
                .adm(bid.getAdm())
                .bidder(adUnitBid.bidderCode)
                .width(adUnitBid.sizes.get(0).getW())
                .height(adUnitBid.sizes.get(0).getH())
                .bidId(adUnitBid.bidId);
    }

    private static void handleException(Throwable exception, BidderDebug.BidderDebugBuilder bidderDebugBuilder,
                                        Future<BidResult> future) {
        logger.warn("Error occurred while sending bid request to an exchange", exception);
        final BidderDebug bidderDebug = bidderDebugBuilder.build();
        future.complete(exception instanceof TimeoutException || exception instanceof ConnectTimeoutException
                ? BidResult.timeout(bidderDebug, "Timed out")
                : BidResult.error(bidderDebug, exception.getMessage()));
    }

    private BidderResult toBidderResult(Bidder bidder, PreBidRequestContext preBidRequestContext,
                                        List<BidResult> bidResults, long bidderStarted) {
        final Integer responseTime = responseTime(bidderStarted);

        final List<Bid> bids = bidResults.stream()
                .filter(br -> Objects.nonNull(br.bidBuilders))
                .flatMap(br -> br.bidBuilders.stream())
                .filter(Objects::nonNull)
                .map(b -> b.responseTimeMs(responseTime))
                .map(Bid.BidBuilder::build)
                .collect(Collectors.toList());

        final BidderStatus.BidderStatusBuilder bidderStatusBuilder = BidderStatus.builder()
                .bidder(bidder.bidderCode)
                .responseTimeMs(responseTime);

        final BidderResult.BidderResultBuilder bidderResultBuilder = BidderResult.builder();

        final BidResult bidResultWithError = findBidResultWithError(bidResults);

        if (!bids.isEmpty()) {
            bidderStatusBuilder.numBids(bids.size());
        } else if (bidResultWithError != null) {
            bidderStatusBuilder.error(bidResultWithError.error);
            bidderResultBuilder.timedOut(bidResultWithError.timedOut);
        } else {
            bidderStatusBuilder.noBid(true);
        }

        if (preBidRequestContext.preBidRequest.app == null
                && preBidRequestContext.uidsCookie.uidFrom(cookieFamily()) == null) {
            bidderStatusBuilder
                    .noCookie(true)
                    .usersync(usersyncInfo());
        }

        if (preBidRequestContext.isDebug) {
            bidderStatusBuilder
                    .debug(bidResults.stream().map(b -> b.bidderDebug).collect(Collectors.toList()));
        }

        return bidderResultBuilder
                .bidderStatus(bidderStatusBuilder.build())
                .bids(bids)
                .build();
    }

    private static BidResult findBidResultWithError(List<BidResult> bidResults) {
        return bidResults.stream()
                .filter(bidResult -> StringUtils.isNotBlank(bidResult.error))
                .reduce((first, second) -> second)
                .orElse(null);
    }

    @Override
    public String code() {
        return "audienceNetwork";
    }

    @Override
    public String cookieFamily() {
        return "audienceNetwork";
    }

    @Override
    public ObjectNode usersyncInfo() {
        return usersyncInfo;
    }

    @AllArgsConstructor(staticName = "of")
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    private static class Params {

        String placementId;

        String pubId;
    }

    @AllArgsConstructor(staticName = "of")
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    private static class AdUnitBidWithParams {

        AdUnitBid adUnitBid;

        Params params;
    }

    @AllArgsConstructor(staticName = "of")
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    private static class BidWithRequest {

        AdUnitBid adUnitBid;

        BidRequest bidRequest;
    }
}
