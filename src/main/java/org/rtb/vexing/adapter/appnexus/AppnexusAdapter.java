package org.rtb.vexing.adapter.appnexus;

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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.experimental.FieldDefaults;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.rtb.vexing.adapter.Adapter;
import org.rtb.vexing.adapter.appnexus.model.AppnexusImpExt;
import org.rtb.vexing.adapter.appnexus.model.AppnexusImpExtAppnexus;
import org.rtb.vexing.adapter.appnexus.model.AppnexusKeyVal;
import org.rtb.vexing.adapter.appnexus.model.AppnexusParams;
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
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AppnexusAdapter implements Adapter {

    private static final Logger logger = LoggerFactory.getLogger(AppnexusAdapter.class);

    private static final String APPLICATION_JSON =
            HttpHeaderValues.APPLICATION_JSON.toString() + ";" + HttpHeaderValues.CHARSET.toString() + "=" + "utf-8";

    private static final Set<MediaType> ALLOWED_MEDIA_TYPES = Collections.unmodifiableSet(
            EnumSet.of(MediaType.banner, MediaType.video));

    private static final int AD_POSITION_ABOVE_THE_FOLD = 1; // openrtb.AdPosition.AdPositionAboveTheFold
    private static final int AD_POSITION_BELOW_THE_FOLD = 3; // openrtb.AdPosition.AdPositionBelowTheFold

    // UsersyncInfo and AppnexusParams fields are not in snake-case
    private static final ObjectMapper DEFAULT_NAMING_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final Clock CLOCK = Clock.systemDefaultZone();

    private final String endpointUrl;
    private final ObjectNode usersyncInfo;
    private final HttpClient httpClient;

    public AppnexusAdapter(String endpointUrl, String usersyncUrl, String externalUrl, HttpClient httpClient) {
        this.endpointUrl = validateUrl(Objects.requireNonNull(endpointUrl));

        Objects.requireNonNull(usersyncUrl);
        Objects.requireNonNull(externalUrl);
        usersyncInfo = createUsersyncInfo(usersyncUrl, externalUrl);

        this.httpClient = Objects.requireNonNull(httpClient);
    }

    private static String validateUrl(String url) {
        try {
            return new URL(url).toString();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("URL supplied is not valid", e);
        }
    }

    @Override
    public Future<BidderResult> requestBids(Bidder bidder, PreBidRequestContext preBidRequestContext) {
        Objects.requireNonNull(bidder);
        Objects.requireNonNull(preBidRequestContext);

        final long bidderStarted = CLOCK.millis();

        final BidRequestWithUrl bidRequestWithUrl;
        try {
            bidRequestWithUrl = createBidRequest(endpointUrl, bidder, preBidRequestContext);
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

        return requestExchange(bidRequestWithUrl, bidder, preBidRequestContext.timeout)
                .compose(bidResult -> toBidderResult(bidResult, bidder, preBidRequestContext, bidderStarted));
    }

    private BidRequestWithUrl createBidRequest(String endpointUrl, Bidder bidder,
                                               PreBidRequestContext preBidRequestContext) {
        validateAdUnitBidsMediaTypes(bidder.adUnitBids);

        final List<AdUnitBidWithParams> adUnitBidsWithParams = createAdUnitBidsWithParams(bidder.adUnitBids);
        final List<Imp> imps = makeImps(adUnitBidsWithParams, preBidRequestContext);
        validateImps(imps);

        final BidRequest bidRequest = BidRequest.builder()
                .id(preBidRequestContext.preBidRequest.tid)
                .at(1)
                .tmax(preBidRequestContext.timeout)
                .imp(imps)
                .app(preBidRequestContext.preBidRequest.app)
                .site(makeSite(preBidRequestContext))
                .device(makeDevice(preBidRequestContext))
                .user(makeUser(preBidRequestContext))
                .source(makeSource(preBidRequestContext))
                .build();

        return BidRequestWithUrl.builder()
                .bidRequest(bidRequest)
                .endpointUrl(endpointUrl(endpointUrl, adUnitBidsWithParams))
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

    private static List<AdUnitBidWithParams> createAdUnitBidsWithParams(List<AdUnitBid> adUnitBids) {
        return adUnitBids.stream()
                .map(adUnitBid -> AdUnitBidWithParams.builder()
                        .adUnitBid(adUnitBid)
                        .params(parseAndValidateParams(adUnitBid))
                        .build())
                .collect(Collectors.toList());
    }

    private static AppnexusParams parseAndValidateParams(AdUnitBid adUnitBid) {
        if (adUnitBid.params == null) {
            throw new PreBidException("Appnexus params section is missing");
        }

        final AppnexusParams params;
        try {
            params = DEFAULT_NAMING_MAPPER.convertValue(adUnitBid.params, AppnexusParams.class);
        } catch (IllegalArgumentException e) {
            // a weird way to pass parsing exception
            throw new PreBidException(e.getMessage(), e.getCause());
        }

        if ((Objects.isNull(params.placementId) || Objects.equals(params.placementId, 0))
                && (StringUtils.isEmpty(params.invCode) || StringUtils.isEmpty(params.member))) {
            throw new PreBidException("No placement or member+invcode provided");
        }
        return params;
    }

    private static List<Imp> makeImps(List<AdUnitBidWithParams> adUnitBidsWithParams,
                                      PreBidRequestContext preBidRequestContext) {
        return adUnitBidsWithParams.stream()
                .flatMap(adUnitBidWithParams -> makeImpsForAdUnitBid(adUnitBidWithParams, preBidRequestContext))
                .collect(Collectors.toList());
    }

    private static void validateImps(List<Imp> imps) {
        if (imps.isEmpty()) {
            throw new PreBidException("openRTB bids need at least one Imp");
        }
    }

    private static Stream<Imp> makeImpsForAdUnitBid(AdUnitBidWithParams adUnitBidWithParams,
                                                    PreBidRequestContext preBidRequestContext) {
        final AdUnitBid adUnitBid = adUnitBidWithParams.adUnitBid;
        final AppnexusParams params = adUnitBidWithParams.params;

        return allowedMediaTypes(adUnitBid).stream()
                .map(mediaType -> impBuilderWithMedia(mediaType, adUnitBid, params)
                        .id(adUnitBid.adUnitCode)
                        .instl(adUnitBid.instl)
                        .secure(preBidRequestContext.secure)
                        .tagid(StringUtils.stripToNull(params.invCode))
                        .bidfloor(bidfloor(params))
                        .ext(Json.mapper.valueToTree(makeImpExt(params))).build());
    }

    private static Set<MediaType> allowedMediaTypes(AdUnitBid adUnitBid) {
        return ALLOWED_MEDIA_TYPES.stream()
                .filter(adUnitBid.mediaTypes::contains)
                .collect(Collectors.toSet());
    }

    private static Imp.ImpBuilder impBuilderWithMedia(MediaType mediaType, AdUnitBid adUnitBid, AppnexusParams params) {
        final Imp.ImpBuilder impBuilder = Imp.builder();

        switch (mediaType) {
            case video:
                impBuilder.video(makeVideo(adUnitBid));
                break;
            case banner:
                impBuilder.banner(makeBanner(adUnitBid, params.position));
                break;
            default:
                // unknown media type, just skip it
        }
        return impBuilder;
    }

    private static Float bidfloor(AppnexusParams params) {
        return params.reserve != null && params.reserve.compareTo(BigDecimal.ZERO) > 0
                ? params.reserve.floatValue() // TODO: we need to factor in currency here if non-USD
                : null;
    }

    private static Banner makeBanner(AdUnitBid adUnitBid, String position) {
        final Banner.BannerBuilder builder = Banner.builder()
                .w(adUnitBid.sizes.get(0).getW())
                .h(adUnitBid.sizes.get(0).getH())
                .format(adUnitBid.sizes)
                .topframe(adUnitBid.topframe);

        if (Objects.equals(position, "above")) {
            builder.pos(AD_POSITION_ABOVE_THE_FOLD);
        } else if (Objects.equals(position, "below")) {
            builder.pos(AD_POSITION_BELOW_THE_FOLD);
        }

        return builder.build();
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

    private static AppnexusImpExt makeImpExt(AppnexusParams params) {
        return AppnexusImpExt.builder()
                .appnexus(AppnexusImpExtAppnexus.builder()
                        .placementId(params.placementId)
                        .keywords(makeKeywords(params))
                        .trafficSourceCode(params.trafficSourceCode)
                        .build())
                .build();
    }

    private static String makeKeywords(AppnexusParams params) {
        if (CollectionUtils.isEmpty(params.keywords)) {
            return null;
        }
        final List<String> kvs = new ArrayList<>();
        for (AppnexusKeyVal keyVal : params.keywords) {
            if (keyVal.values == null || keyVal.values.isEmpty()) {
                kvs.add(keyVal.key);
            } else {
                for (String value : keyVal.values) {
                    kvs.add(String.format("%s=%s", keyVal.key, value));
                }
            }
        }
        return kvs.stream().collect(Collectors.joining(","));
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
        final Source.SourceBuilder sourceBuilder = Source.builder()
                .tid(preBidRequestContext.preBidRequest.tid);

        if (preBidRequestContext.preBidRequest.app == null) {
            sourceBuilder.fd(1); // upstream, aka header
        }

        return sourceBuilder.build();
    }

    private static String endpointUrl(String endpointUrl, List<AdUnitBidWithParams> adUnitBidsWithParams) {
        return adUnitBidsWithParams.stream()
                .map(adUnitBidWithParams -> adUnitBidWithParams.params)
                .filter(params -> StringUtils.isNotEmpty(params.invCode) && StringUtils.isNotBlank(params.member))
                .reduce((first, second) -> second)
                .map(params -> String.format("%s%s", endpointUrl, String.format("?member_id=%s", params.member)))
                .orElse(endpointUrl);
    }

    private Future<BidResult> requestExchange(BidRequestWithUrl bidRequestWithUrl, Bidder bidder, long timeout) {
        final String bidRequestBody = Json.encode(bidRequestWithUrl.bidRequest);

        final BidderDebug.BidderDebugBuilder bidderDebugBuilder = beginBidderDebug(bidRequestWithUrl.endpointUrl,
                bidRequestBody);

        final Future<BidResult> future = Future.future();
        httpClient.postAbs(bidRequestWithUrl.endpointUrl,
                response -> handleResponse(bidRequestWithUrl.bidRequest, response, bidder, bidderDebugBuilder, future))
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

    private void handleResponse(BidRequest bidRequest, HttpClientResponse response, Bidder bidder,
                                BidderDebug.BidderDebugBuilder bidderDebugBuilder, Future<BidResult> future) {
        response
                .bodyHandler(buffer -> future.complete(
                        toBidResult(bidRequest, bidder, bidderDebugBuilder, response.statusCode(),
                                buffer.toString())))
                .exceptionHandler(exception -> handleException(exception, bidderDebugBuilder, future));
    }

    private BidResult toBidResult(BidRequest bidRequest, Bidder bidder,
                                  BidderDebug.BidderDebugBuilder bidderDebugBuilder, int statusCode, String body) {
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
            logger.warn("Error occurred while parsing bid response: {0}", body, e);
            return BidResult.error(bidderDebug, e.getMessage());
        }

        try {
            final List<Bid.BidBuilder> bids = extractBids(bidRequest, bidResponse, bidder);
            return BidResult.success(bids, bidderDebug);
        } catch (PreBidException e) {
            logger.warn("Error occurred while extracting bids", e);
            return BidResult.error(bidderDebug, e.getMessage());
        }
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

    private static int responseTime(long bidderStarted) {
        return Math.toIntExact(CLOCK.millis() - bidderStarted);
    }

    private List<Bid.BidBuilder> extractBids(BidRequest bidRequest, BidResponse bidResponse, Bidder bidder) {
        return bidResponse == null || bidResponse.getSeatbid() == null ? null
                : bidResponse.getSeatbid().stream()
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
                        .mediaType(mediaTypeFor(bid.getImpid(), bidRequest))
                        .width(bid.getW())
                        .height(bid.getH())
                        .dealId(bid.getDealid())
                        .nurl(bid.getNurl()))
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

    private static MediaType mediaTypeFor(String impId, BidRequest bidRequest) {
        MediaType mediaType = MediaType.banner;
        for (Imp imp : bidRequest.getImp()) {
            if (Objects.equals(imp.getId(), impId)) {
                if (imp.getVideo() != null) {
                    mediaType = MediaType.video;
                }
                break;
            }
        }
        return mediaType;
    }

    private void handleException(Throwable exception, BidderDebug.BidderDebugBuilder bidderDebugBuilder,
                                 Future<BidResult> future) {
        logger.warn("Error occurred while sending bid request to an exchange", exception);
        final BidderDebug bidderDebug = bidderDebugBuilder.build();
        future.complete(exception instanceof TimeoutException || exception instanceof ConnectTimeoutException
                ? BidResult.timeout(bidderDebug, "Timed out")
                : BidResult.error(bidderDebug, exception.getMessage()));
    }

    @Override
    public String code() {
        return "appnexus";
    }

    @Override
    public String cookieFamily() {
        return "adnxs";
    }

    @Override
    public ObjectNode usersyncInfo() {
        return usersyncInfo;
    }

    private static ObjectNode createUsersyncInfo(String usersyncUrl, String externalUrl) {
        final String redirectUri;
        try {
            redirectUri = URLEncoder.encode(String.format("%s/setuid?bidder=adnxs&uid=$UID", externalUrl), "UTF-8");
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

    @Builder
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    private static class AdUnitBidWithParams {

        AdUnitBid adUnitBid;

        AppnexusParams params;
    }

    @Builder
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    private static class BidRequestWithUrl {

        String endpointUrl;

        BidRequest bidRequest;
    }
}
