package org.rtb.vexing.adapter.pubmatic;

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
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Cookie;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.rtb.vexing.adapter.Adapter;
import org.rtb.vexing.adapter.pubmatic.model.PubmaticParams;
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

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.time.Clock;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PubmaticAdapter implements Adapter {

    private static final Logger logger = LoggerFactory.getLogger(PubmaticAdapter.class);

    // UsersyncInfo and PubmaticParams fields are not in snake-case
    private static final ObjectMapper DEFAULT_NAMING_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final Clock CLOCK = Clock.systemDefaultZone();

    private static final Set<MediaType> ALLOWED_MEDIA_TYPES = Collections.unmodifiableSet(
            EnumSet.of(MediaType.banner, MediaType.video));

    private static final String APPLICATION_JSON =
            HttpHeaderValues.APPLICATION_JSON.toString() + ";" + HttpHeaderValues.CHARSET.toString() + "=" + "utf-8";

    private final String endpointUrl;
    private final ObjectNode usersyncInfo;
    private final HttpClient httpClient;

    public PubmaticAdapter(String endpointUrl, String usersyncUrl, String externalUrl, HttpClient httpClient) {
        this.endpointUrl = validateUrl(Objects.requireNonNull(endpointUrl));

        usersyncInfo = createUsersyncInfo(Objects.requireNonNull(usersyncUrl), Objects.requireNonNull(externalUrl));

        this.httpClient = Objects.requireNonNull(httpClient);
    }

    private static String validateUrl(String url) {
        try {
            return new URL(url).toString();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("URL supplied is not valid", e);
        }
    }

    private static ObjectNode createUsersyncInfo(String usersyncUrl, String externalUrl) {
        final String redirectUri;
        try {
            redirectUri = URLEncoder.encode(String.format("%s/setuid?bidder=pubmatic&uid=", externalUrl), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new PreBidException("Cannot encode redirect uri");
        }

        final UsersyncInfo usersyncInfo = UsersyncInfo.builder()
                .url(String.format("%s%s", usersyncUrl, redirectUri))
                .type("iframe")
                .supportCORS(false)
                .build();

        return DEFAULT_NAMING_MAPPER.valueToTree(usersyncInfo);
    }

    @Override
    public Future<BidderResult> requestBids(Bidder bidder, PreBidRequestContext preBidRequestContext) {
        Objects.requireNonNull(bidder);
        Objects.requireNonNull(preBidRequestContext);

        final long bidderStarted = CLOCK.millis();

        final BidRequest bidRequest;
        try {
            bidRequest = createBidRequest(bidder, preBidRequestContext);
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

        return requestExchange(bidRequest, bidder, preBidRequestContext.timeout, preBidRequestContext)
                .compose(bidResult -> toBidderResult(bidResult, bidder, preBidRequestContext, bidderStarted));
    }

    private BidRequest createBidRequest(Bidder bidder, PreBidRequestContext preBidRequestContext) {
        validateAdUnitBidsMediaTypes(bidder.adUnitBids);

        final List<AdUnitBidWithParams> adUnitBidsWithParams = createAdUnitBidsWithParams(bidder.adUnitBids,
                preBidRequestContext.preBidRequest.tid);
        final List<Imp> imps = validateImps(makeImps(adUnitBidsWithParams, preBidRequestContext));

        final Publisher publisher = makePublisher(preBidRequestContext, adUnitBidsWithParams);

        return BidRequest.builder()
                .id(preBidRequestContext.preBidRequest.tid)
                .at(1)
                .tmax(preBidRequestContext.timeout)
                .imp(imps)
                .app(makeApp(preBidRequestContext, publisher))
                .site(makeSite(preBidRequestContext, publisher))
                .device(makeDevice(preBidRequestContext))
                .user(makeUser(preBidRequestContext))
                .source(makeSource(preBidRequestContext))
                .build();
    }

    private static void validateAdUnitBidsMediaTypes(List<AdUnitBid> adUnitBids) {
        if (!adUnitBids.stream()
                .allMatch(adUnitBid -> adUnitBid.mediaTypes.stream()
                        .allMatch(mediaType -> isValidAdUnitBidMediaType(mediaType, adUnitBid)))) {
            throw new PreBidException("Invalid AdUnit: VIDEO media type with no video data");
        }
    }

    private static boolean isValidAdUnitBidMediaType(MediaType mediaType, AdUnitBid adUnitBid) {
        return !(MediaType.video.equals(mediaType)
                && (adUnitBid.video == null || CollectionUtils.isEmpty(adUnitBid.video.mimes)));
    }

    private static List<AdUnitBidWithParams> createAdUnitBidsWithParams(List<AdUnitBid> adUnitBids, String requestId) {
        final List<AdUnitBidWithParams> adUnitBidWithParams = adUnitBids.stream()
                .map(adUnitBid -> AdUnitBidWithParams.of(adUnitBid, parseAndValidateParams(adUnitBid, requestId)))
                .collect(Collectors.toList());

        // at least one adUnitBid of banner type must be with valid params
        if (adUnitBidWithParams.stream().noneMatch(PubmaticAdapter::isValidParams)) {
            throw new PreBidException("Incorrect adSlot / Publisher param");
        }

        return adUnitBidWithParams;
    }

    private static boolean isValidParams(AdUnitBidWithParams adUnitBidWithParams) {
        final Params params = adUnitBidWithParams.params;

        // if adUnitBid has banner type, params should contains tagId, width and height fields
        return !adUnitBidWithParams.adUnitBid.mediaTypes.contains(MediaType.banner)
                || params != null && ObjectUtils.allNotNull(params.tagId, params.width, params.height);
    }

    private static Params parseAndValidateParams(AdUnitBid adUnitBid, String requestId) {
        final PubmaticParams pubmaticParams;
        try {
            pubmaticParams = DEFAULT_NAMING_MAPPER.convertValue(adUnitBid.params, PubmaticParams.class);
        } catch (IllegalArgumentException e) {
            logWrongParams(requestId, null, adUnitBid, "Ignored bid: invalid JSON  [%s] err [%s]", adUnitBid.params, e);
            return null;
        }

        final String publisherId = pubmaticParams.publisherId;
        if (StringUtils.isEmpty(publisherId)) {
            logWrongParams(requestId, publisherId, adUnitBid, "Ignored bid: Publisher Id missing");
            return null;
        }

        final String adSlot = StringUtils.trimToNull(pubmaticParams.adSlot);
        if (StringUtils.isEmpty(adSlot)) {
            logWrongParams(requestId, publisherId, adUnitBid, "Ignored bid: adSlot missing");
            return null;
        }

        final String[] adSlots = adSlot.split("@");
        if (adSlots.length != 2 || StringUtils.isEmpty(adSlots[0]) || StringUtils.isEmpty(adSlots[1])) {
            logWrongParams(requestId, publisherId, adUnitBid, "Ignored bid: invalid adSlot [%s]", adSlot);
            return null;
        }

        final String[] adSizes = adSlots[1].toLowerCase().split("x");
        if (adSizes.length != 2) {
            logWrongParams(requestId, publisherId, adUnitBid, "Ignored bid: invalid adSize [%s]", adSlots[1]);
            return null;
        }

        final int width;
        try {
            width = Integer.parseInt(adSizes[0].trim());
        } catch (NumberFormatException e) {
            logWrongParams(requestId, publisherId, adUnitBid, "Ignored bid: invalid adSlot width [%s]", adSizes[0]);
            return null;
        }

        final int height;
        final String[] adSizeHeights = adSizes[1].split(":");
        try {
            height = Integer.parseInt(adSizeHeights[0].trim());
        } catch (NumberFormatException e) {
            logWrongParams(requestId, publisherId, adUnitBid, "Ignored bid: invalid adSlot height [%s]", adSizes[0]);
            return null;
        }

        return Params.of(publisherId, adSlot, adSlots[0], width, height);
    }

    private static void logWrongParams(String requestId, String publisherId, AdUnitBid adUnitBid, String errorMessage,
                                       Object... args) {
        logger.warn("[PUBMATIC] ReqID [{0}] PubID [{1}] AdUnit [{2}] BidID [{3}] {4}", requestId, publisherId,
                adUnitBid.adUnitCode, adUnitBid.bidId, String.format(errorMessage, args));
    }

    private static List<Imp> makeImps(List<AdUnitBidWithParams> adUnitBidsWithParams,
                                      PreBidRequestContext preBidRequestContext) {
        return adUnitBidsWithParams.stream()
                .flatMap(adUnitBidWithParams -> makeImpsForAdUnitBid(adUnitBidWithParams, preBidRequestContext))
                .collect(Collectors.toList());
    }

    private static Stream<Imp> makeImpsForAdUnitBid(AdUnitBidWithParams adUnitBidWithParams,
                                                    PreBidRequestContext preBidRequestContext) {
        final AdUnitBid adUnitBid = adUnitBidWithParams.adUnitBid;
        final Params params = adUnitBidWithParams.params;

        return allowedMediaTypes(adUnitBid).stream()
                .map(mediaType -> impBuilderWithMedia(mediaType, adUnitBid, params)
                        .id(adUnitBid.adUnitCode)
                        .instl(adUnitBid.instl)
                        .secure(preBidRequestContext.secure)
                        .tagid(mediaType == MediaType.banner && params != null ? params.tagId : null)
                        .build());
    }

    private static Set<MediaType> allowedMediaTypes(AdUnitBid adUnitBid) {
        return ALLOWED_MEDIA_TYPES.stream()
                .filter(adUnitBid.mediaTypes::contains)
                .collect(Collectors.toSet());
    }

    private static Imp.ImpBuilder impBuilderWithMedia(MediaType mediaType, AdUnitBid adUnitBid, Params params) {
        final Imp.ImpBuilder impBuilder = Imp.builder();

        switch (mediaType) {
            case video:
                impBuilder.video(makeVideo(adUnitBid));
                break;
            case banner:
                impBuilder.banner(makeBanner(adUnitBid, params));
                break;
            default:
                // unknown media type, just skip it
        }
        return impBuilder;
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

    private static Banner makeBanner(AdUnitBid adUnitBid, Params params) {
        return Banner.builder()
                .w(params != null ? params.width : adUnitBid.sizes.get(0).getW())
                .h(params != null ? params.height : adUnitBid.sizes.get(0).getH())
                .format(params != null ? null : adUnitBid.sizes) // pubmatic doesn't support
                .topframe(adUnitBid.topframe)
                .build();
    }

    private static List<Imp> validateImps(List<Imp> imps) {
        if (imps.isEmpty()) {
            throw new PreBidException("openRTB bids need at least one Imp");
        }
        return imps;
    }

    private static Publisher makePublisher(PreBidRequestContext preBidRequestContext,
                                           List<AdUnitBidWithParams> adUnitBidsWithParams) {
        return adUnitBidsWithParams.stream()
                .filter(adUnitBidWithParams -> adUnitBidWithParams.params != null
                        && adUnitBidWithParams.params.publisherId != null
                        && adUnitBidWithParams.params.adSlot != null)
                .map(adUnitBidWithParams -> adUnitBidWithParams.params.publisherId)
                .reduce((first, second) -> second)
                .map(publisherId -> Publisher.builder().id(publisherId).domain(preBidRequestContext.domain).build())
                .orElse(null);
    }

    private static App makeApp(PreBidRequestContext preBidRequestContext, Publisher publisher) {
        final App app = preBidRequestContext.preBidRequest.app;
        return app == null ? null : app.toBuilder()
                .publisher(publisher)
                .build();
    }

    private static Site makeSite(PreBidRequestContext preBidRequestContext, Publisher publisher) {
        return preBidRequestContext.preBidRequest.app != null ? null : Site.builder()
                .domain(preBidRequestContext.domain)
                .page(preBidRequestContext.referer)
                .publisher(publisher)
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
        final Source.SourceBuilder sourceBuilder = Source.builder()
                .tid(preBidRequestContext.preBidRequest.tid);

        if (preBidRequestContext.preBidRequest.app == null) {
            sourceBuilder.fd(1); // upstream, aka header
        }

        return sourceBuilder.build();
    }

    private static int responseTime(long bidderStarted) {
        return Math.toIntExact(CLOCK.millis() - bidderStarted);
    }

    private Future<BidResult> requestExchange(BidRequest bidRequest, Bidder bidder, long timeout,
                                              PreBidRequestContext preBidRequestContext) {
        final String bidRequestBody = Json.encode(bidRequest);

        final BidderDebug.BidderDebugBuilder bidderDebugBuilder = beginBidderDebug(endpointUrl, bidRequestBody);

        final Future<BidResult> future = Future.future();
        httpClient.postAbs(endpointUrl, response -> handleResponse(response, bidder, bidderDebugBuilder, future))
                .exceptionHandler(exception -> handleException(exception, bidderDebugBuilder, future))
                .putHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                .putHeader(HttpHeaders.ACCEPT, HttpHeaderValues.APPLICATION_JSON)
                .putHeader(HttpHeaders.SET_COOKIE, makeUserCookie(preBidRequestContext))
                .setTimeout(timeout)
                .end(bidRequestBody);
        return future;
    }

    private BidderDebug.BidderDebugBuilder beginBidderDebug(String url, String bidRequestBody) {
        return BidderDebug.builder()
                .requestUri(url)
                .requestBody(bidRequestBody);
    }

    private static BidderDebug completeBidderDebug(BidderDebug.BidderDebugBuilder bidderDebugBuilder,
                                                   int statusCode, String body) {
        return bidderDebugBuilder
                .responseBody(body)
                .statusCode(statusCode)
                .build();
    }

    private void handleResponse(HttpClientResponse response, Bidder bidder,
                                BidderDebug.BidderDebugBuilder bidderDebugBuilder, Future<BidResult> future) {
        response
                .bodyHandler(buffer -> future.complete(toBidResult(bidder, bidderDebugBuilder, response.statusCode(),
                        buffer.toString())))
                .exceptionHandler(exception -> handleException(exception, bidderDebugBuilder, future));
    }

    private BidResult toBidResult(Bidder bidder, BidderDebug.BidderDebugBuilder bidderDebugBuilder, int statusCode,
                                  String body) {
        final BidderDebug bidderDebug = completeBidderDebug(bidderDebugBuilder, statusCode, body);

        if (statusCode == 204) {
            return BidResult.empty(bidderDebug);
        }
        if (statusCode != 200) {
            logger.warn("Bid response code is {0}, body: {1}", statusCode, body);
            return BidResult.error(bidderDebug, String.format("HTTP status %d", statusCode));
        }

        final BidResponse bidResponse;
        try {
            bidResponse = Json.decodeValue(body, BidResponse.class);
        } catch (DecodeException e) {
            logger.warn("Error occurred while parsing bid response: {0}", e, body);
            return BidResult.error(bidderDebug, e.getMessage());
        }

        try {
            final List<Bid.BidBuilder> bids = extractBids(bidResponse, bidder);
            return BidResult.success(bids, bidderDebug);
        } catch (PreBidException e) {
            logger.warn("Error occurred while extracting bids", e);
            return BidResult.error(bidderDebug, e.getMessage());
        }
    }

    private List<Bid.BidBuilder> extractBids(BidResponse bidResponse, Bidder bidder) {
        return bidResponse == null || bidResponse.getSeatbid() == null ? null : bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .filter(seatBid -> seatBid.getBid() != null)
                .flatMap(seatBid -> seatBid.getBid().stream())
                .filter(Objects::nonNull)
                .map(bid -> Bid.builder()
                        .bidId(lookupBidId(bidder.adUnitBids, bid.getImpid()))
                        .code(bid.getImpid())
                        .bidder(bidder.bidderCode)
                        .price(bid.getPrice())
                        .adm(bid.getAdm())
                        .creativeId(bid.getCrid())
                        .width(bid.getW())
                        .height(bid.getH())
                        .dealId(bid.getDealid()))
                .collect(Collectors.toList());
    }

    private static String lookupBidId(List<AdUnitBid> adUnitBids, String code) {
        for (AdUnitBid adUnitBid : adUnitBids) {
            if (Objects.equals(adUnitBid.adUnitCode, code)) {
                return adUnitBid.bidId;
            }
        }
        throw new PreBidException(String.format("Unknown ad unit code '%s'", code));
    }

    private void handleException(Throwable exception, BidderDebug.BidderDebugBuilder bidderDebugBuilder,
                                 Future<BidResult> future) {
        logger.warn("Error occurred while sending bid request to an exchange", exception);
        final BidderDebug bidderDebug = bidderDebugBuilder.build();
        future.complete(exception instanceof TimeoutException || exception instanceof ConnectTimeoutException
                ? BidResult.timeout(bidderDebug, "Timed out")
                : BidResult.error(bidderDebug, exception.getMessage()));
    }

    private String makeUserCookie(PreBidRequestContext preBidRequestContext) {
        final String cookieValue = preBidRequestContext.uidsCookie.uidFrom(cookieFamily());
        return Cookie.cookie("KADUSERCOOKIE", ObjectUtils.firstNonNull(cookieValue, "")).encode();
    }

    private Future<BidderResult> toBidderResult(BidResult bidResult, Bidder bidder,
                                                PreBidRequestContext preBidRequestContext, long bidderStarted) {
        final Integer responseTime = responseTime(bidderStarted);

        final BidderStatus.BidderStatusBuilder bidderStatusBuilder = BidderStatus.builder()
                .bidder(bidder.bidderCode)
                .responseTimeMs(responseTime);

        if (preBidRequestContext.preBidRequest.app == null
                && preBidRequestContext.uidsCookie.uidFrom(cookieFamily()) == null) {
            bidderStatusBuilder
                    .noCookie(true)
                    .usersync(usersyncInfo());
        }
        if (preBidRequestContext.isDebug) {
            bidderStatusBuilder.debug(Collections.singletonList(bidResult.bidderDebug));
        }

        final BidderResult.BidderResultBuilder bidderResultBuilder = BidderResult.builder();

        if (StringUtils.isNotBlank(bidResult.error)) {
            bidderStatusBuilder.error(bidResult.error);
            bidderResultBuilder
                    .timedOut(bidResult.timedOut)
                    .bids(Collections.emptyList());
        } else {
            final List<Bid> bids = CollectionUtils.isEmpty(bidResult.bidBuilders) ? Collections.emptyList()
                    : bidResult.bidBuilders.stream()
                    .map(bidBuilder -> bidBuilder.responseTimeMs(responseTime))
                    .map(Bid.BidBuilder::build)
                    .collect(Collectors.toList());
            bidderResultBuilder.bids(bids);

            if (CollectionUtils.isEmpty(bids)) {
                bidderStatusBuilder.noBid(true);
            } else {
                bidderStatusBuilder.numBids(bids.size());
            }
        }

        return Future.succeededFuture(bidderResultBuilder
                .bidderStatus(bidderStatusBuilder.build())
                .build());
    }

    @Override
    public String code() {
        return "Pubmatic";
    }

    @Override
    public String cookieFamily() {
        return "pubmatic";
    }

    @Override
    public ObjectNode usersyncInfo() {
        return usersyncInfo;
    }

    @AllArgsConstructor(staticName = "of")
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    private static class Params {

        String publisherId;

        String adSlot;

        String tagId;

        Integer width;

        Integer height;
    }

    @AllArgsConstructor(staticName = "of")
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    private static class AdUnitBidWithParams {

        AdUnitBid adUnitBid;

        Params params;
    }
}
