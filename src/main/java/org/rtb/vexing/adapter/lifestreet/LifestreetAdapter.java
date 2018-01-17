package org.rtb.vexing.adapter.lifestreet;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
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
import org.rtb.vexing.adapter.lifestreet.model.LifestreetParams;
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

public class LifestreetAdapter implements Adapter {

    private static final Logger logger = LoggerFactory.getLogger(LifestreetAdapter.class);

    // UsersyncInfo fields are not in snake-case
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

    public LifestreetAdapter(String endpointUrl, String usersyncUrl, String externalUrl, HttpClient httpClient) {
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
            redirectUri = URLEncoder.encode(String.format("%s/setuid?bidder=lifestreet&uid=$$visitor_cookie$$",
                    externalUrl), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new PreBidException("Cannot encode redirect uri");
        }

        final UsersyncInfo usersyncInfo = UsersyncInfo.builder()
                .url(String.format("%s%s", usersyncUrl, redirectUri))
                .type("redirect")
                .supportCORS(false)
                .build();

        return DEFAULT_NAMING_MAPPER.valueToTree(usersyncInfo);
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
                .forEach(LifestreetAdapter::validateVideoMediaType);
    }

    private static void validateVideoMediaType(AdUnitBid adUnitBid) {
        if (adUnitBid.video == null || CollectionUtils.isEmpty(adUnitBid.video.mimes)) {
            throw new PreBidException("Invalid AdUnit: VIDEO media type with no video data");
        }
    }

    private static List<AdUnitBidWithParams> createAdUnitBidsWithParams(List<AdUnitBid> adUnitBids) {
        return adUnitBids.stream()
                .map(adUnitBid -> AdUnitBidWithParams.of(adUnitBid, parseAndValidateParams(adUnitBid)))
                .collect(Collectors.toList());
    }

    private static LifestreetParams parseAndValidateParams(AdUnitBid adUnitBid) {
        if (adUnitBid.params == null) {
            throw new PreBidException("Lifestreet params section is missing");
        }

        final LifestreetParams params;
        try {
            params = Json.mapper.convertValue(adUnitBid.params, LifestreetParams.class);
        } catch (IllegalArgumentException e) {
            // a weird way to pass parsing exception
            throw new PreBidException(e.getMessage(), e.getCause());
        }

        if (StringUtils.isEmpty(params.slotTag)) {
            throw new PreBidException("Missing slot_tag param");
        }
        if (params.slotTag.split("\\.").length != 2) {
            throw new PreBidException(String.format("Invalid slot_tag param '%s'", params.slotTag));
        }

        return params;
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
                        .app(preBidRequestContext.preBidRequest.app)
                        .site(makeSite(preBidRequestContext))
                        .device(makeDevice(preBidRequestContext))
                        .user(makeUser(preBidRequestContext))
                        .source(makeSource(preBidRequestContext))
                        .build()));
    }

    private static List<Imp> makeImps(AdUnitBidWithParams adUnitBidWithParams,
                                      PreBidRequestContext preBidRequestContext) {
        final AdUnitBid adUnitBid = adUnitBidWithParams.adUnitBid;
        final LifestreetParams params = adUnitBidWithParams.params;

        return allowedMediaTypes(adUnitBid).stream()
                .map(mediaType -> impBuilderWithMedia(mediaType, adUnitBid)
                        .id(adUnitBid.adUnitCode)
                        .instl(adUnitBid.instl)
                        .secure(preBidRequestContext.secure)
                        .tagid(params.slotTag)
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
        return Banner.builder()
                .w(adUnitBid.sizes.get(0).getW())
                .h(adUnitBid.sizes.get(0).getH())
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

    private static Site makeSite(PreBidRequestContext preBidRequestContext) {
        return preBidRequestContext.preBidRequest.app != null ? null : Site.builder()
                .domain(preBidRequestContext.domain)
                .page(preBidRequestContext.referer)
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

        final BidderDebug.BidderDebugBuilder bidderDebugBuilder = beginBidderDebug(endpointUrl, bidRequestBody);

        final Future<BidResult> future = Future.future();
        httpClient.postAbs(endpointUrl, response -> handleResponse(response, adUnitBid, bidderDebugBuilder, future))
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

        if (statusCode == 204) {
            return BidResult.empty(bidderDebug);
        }
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
                .creativeId(bid.getCrid())
                .width(bid.getW())
                .height(bid.getH())
                .dealId(bid.getDealid())
                .nurl(bid.getNurl())
                .bidder(adUnitBid.bidderCode)
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
        return "Lifestreet";
    }

    @Override
    public String cookieFamily() {
        return "lifestreet";
    }

    @Override
    public ObjectNode usersyncInfo() {
        return usersyncInfo;
    }

    @AllArgsConstructor(staticName = "of")
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    private static class AdUnitBidWithParams {

        AdUnitBid adUnitBid;

        LifestreetParams params;
    }

    @AllArgsConstructor(staticName = "of")
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    private static class BidWithRequest {

        AdUnitBid adUnitBid;

        BidRequest bidRequest;
    }
}
