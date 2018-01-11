package org.rtb.vexing.adapter.indexexchange;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.rtb.vexing.adapter.Adapter;
import org.rtb.vexing.adapter.indexexchange.model.IndexExchangeParams;
import org.rtb.vexing.exception.PreBidException;
import org.rtb.vexing.model.AdUnitBid;
import org.rtb.vexing.model.BidResult;
import org.rtb.vexing.model.Bidder;
import org.rtb.vexing.model.BidderResult;
import org.rtb.vexing.model.MediaType;
import org.rtb.vexing.model.PreBidRequestContext;
import org.rtb.vexing.model.request.PreBidRequest;
import org.rtb.vexing.model.response.Bid;
import org.rtb.vexing.model.response.BidderDebug;
import org.rtb.vexing.model.response.BidderStatus;
import org.rtb.vexing.model.response.UsersyncInfo;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Clock;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class IndexExchangeAdapter implements Adapter {

    private static final Logger logger = LoggerFactory.getLogger(IndexExchangeAdapter.class);

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

    public IndexExchangeAdapter(String endpointUrl, String usersyncUrl, HttpClient httpClient) {
        this.endpointUrl = validateUrl(Objects.requireNonNull(endpointUrl));

        usersyncInfo = createUsersyncInfo(Objects.requireNonNull(usersyncUrl));

        this.httpClient = Objects.requireNonNull(httpClient);
    }

    private static String validateUrl(String url) {
        try {
            return new URL(url).toString();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("URL supplied is not valid", e);
        }
    }

    private static ObjectNode createUsersyncInfo(String usersyncUrl) {
        final UsersyncInfo usersyncInfo = UsersyncInfo.builder()
                .url(usersyncUrl)
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

        final BidRequest bidRequest;
        try {
            validatePreBidRequest(preBidRequestContext.preBidRequest);

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

        return requestExchange(bidRequest, bidder, preBidRequestContext.timeout)
                .compose(bidResult -> toBidderResult(bidResult, bidder, preBidRequestContext, bidderStarted));
    }

    private static void validatePreBidRequest(PreBidRequest preBidRequest) {
        if (preBidRequest.app != null) {
            throw new PreBidException("Index doesn't support apps");
        }
    }

    private BidRequest createBidRequest(Bidder bidder, PreBidRequestContext preBidRequestContext) {
        validateAdUnitBidsMediaTypes(bidder.adUnitBids);

        final List<Imp> imps = validateImps(makeImps(bidder.adUnitBids, preBidRequestContext));

        final Integer siteId = bidder.adUnitBids.stream()
                .map(IndexExchangeAdapter::parseAndValidateParams)
                .map(params -> params.siteId)
                .reduce((first, second) -> second).orElse(null);

        return BidRequest.builder()
                .id(preBidRequestContext.preBidRequest.tid)
                .at(1)
                .tmax(preBidRequestContext.timeout)
                .imp(imps)
                .site(makeSite(preBidRequestContext, siteId))
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

    private static IndexExchangeParams parseAndValidateParams(AdUnitBid adUnitBid) {
        if (adUnitBid.params == null) {
            throw new PreBidException("IndexExchange params section is missing");
        }

        final IndexExchangeParams params;
        try {
            params = Json.mapper.convertValue(adUnitBid.params, IndexExchangeParams.class);
        } catch (IllegalArgumentException e) {
            // a weird way to pass parsing exception
            throw new PreBidException(String.format("unmarshal params '%s' failed: %s", adUnitBid.params,
                    e.getMessage()), e.getCause());
        }

        if (params.siteId == null || params.siteId == 0) {
            throw new PreBidException("Missing siteID param");
        }

        return params;
    }

    private static List<Imp> makeImps(List<AdUnitBid> adUnitBids, PreBidRequestContext preBidRequestContext) {
        return adUnitBids.stream()
                .flatMap(adUnitBid -> makeImpsForAdUnitBid(adUnitBid, preBidRequestContext))
                .collect(Collectors.toList());
    }

    private static Stream<Imp> makeImpsForAdUnitBid(AdUnitBid adUnitBid, PreBidRequestContext preBidRequestContext) {
        return allowedMediaTypes(adUnitBid).stream()
                .map(mediaType -> impBuilderWithMedia(mediaType, adUnitBid)
                        .id(adUnitBid.adUnitCode)
                        .instl(adUnitBid.instl)
                        .secure(preBidRequestContext.secure)
                        .tagid(adUnitBid.adUnitCode)
                        .build());
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

    private static Banner makeBanner(AdUnitBid adUnitBid) {
        return Banner.builder()
                .w(adUnitBid.sizes.get(0).getW())
                .h(adUnitBid.sizes.get(0).getH())
                .format(adUnitBid.sizes)
                .topframe(adUnitBid.topframe)
                .build();
    }

    private static List<Imp> validateImps(List<Imp> imps) {
        if (imps.isEmpty()) {
            throw new PreBidException("openRTB bids need at least one Imp");
        }
        return imps;
    }

    private static Site makeSite(PreBidRequestContext preBidRequestContext, Integer siteId) {
        return Site.builder()
                .domain(preBidRequestContext.domain)
                .page(preBidRequestContext.referer)
                .publisher(Publisher.builder()
                        .id(siteId != null ? String.valueOf(siteId) : null)
                        .build())
                .build();
    }

    private static Device makeDevice(PreBidRequestContext preBidRequestContext) {
        final Device device = preBidRequestContext.preBidRequest.device;

        // create a copy since device might be shared with other adapters
        final Device.DeviceBuilder deviceBuilder = device != null ? device.toBuilder() : Device.builder();

        return deviceBuilder
                .ip(preBidRequestContext.ip)
                .ua(preBidRequestContext.ua)
                .build();
    }

    private User makeUser(PreBidRequestContext preBidRequestContext) {
        return User.builder()
                .buyeruid(preBidRequestContext.uidsCookie.uidFrom(cookieFamily()))
                // id is a UID for "adnxs" (see logic in open-source implementation)
                .id(preBidRequestContext.uidsCookie.uidFrom("adnxs"))
                .build();
    }

    private static Source makeSource(PreBidRequestContext preBidRequestContext) {
        return Source.builder()
                .tid(preBidRequestContext.preBidRequest.tid)
                .fd(1)
                .build();
    }

    private static int responseTime(long bidderStarted) {
        return Math.toIntExact(CLOCK.millis() - bidderStarted);
    }

    private Future<BidResult> requestExchange(BidRequest bidRequest, Bidder bidder, long timeout) {
        final String bidRequestBody = Json.encode(bidRequest);

        final BidderDebug.BidderDebugBuilder bidderDebugBuilder = beginBidderDebug(endpointUrl, bidRequestBody);

        final Future<BidResult> future = Future.future();
        httpClient.postAbs(endpointUrl, response -> handleResponse(response, bidder, bidderDebugBuilder, future))
                .exceptionHandler(exception -> handleException(exception, bidderDebugBuilder, future))
                .putHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                .putHeader(HttpHeaders.ACCEPT, HttpHeaderValues.APPLICATION_JSON)
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
            logger.warn("Error occurred while parsing bid response: {0}", body, e);
            return BidResult.error(bidderDebug, String.format("Error parsing response: %s", e.getMessage()));
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
                .filter(seatBid -> Objects.nonNull(seatBid.getBid()))
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
            bidderResultBuilder.timedOut(bidResult.timedOut);
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
        return "indexExchange";
    }

    @Override
    public String cookieFamily() {
        return "indexExchange";
    }

    @Override
    public ObjectNode usersyncInfo() {
        return usersyncInfo;
    }
}
