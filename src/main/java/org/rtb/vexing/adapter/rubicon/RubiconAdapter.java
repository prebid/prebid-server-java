package org.rtb.vexing.adapter.rubicon;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Content;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.BidResponse;
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
import org.rtb.vexing.adapter.PreBidRequestException;
import org.rtb.vexing.adapter.rubicon.model.RubiconBannerExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconBannerExtRp;
import org.rtb.vexing.adapter.rubicon.model.RubiconDeviceExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconDeviceExtRp;
import org.rtb.vexing.adapter.rubicon.model.RubiconImpExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconImpExtRp;
import org.rtb.vexing.adapter.rubicon.model.RubiconImpExtRpTrack;
import org.rtb.vexing.adapter.rubicon.model.RubiconParams;
import org.rtb.vexing.adapter.rubicon.model.RubiconPubExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconPubExtRp;
import org.rtb.vexing.adapter.rubicon.model.RubiconSiteExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconSiteExtRp;
import org.rtb.vexing.adapter.rubicon.model.RubiconTargetingExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconUserExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconUserExtDt;
import org.rtb.vexing.adapter.rubicon.model.RubiconUserExtRp;
import org.rtb.vexing.adapter.rubicon.model.RubiconVideoExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconVideoExtRP;
import org.rtb.vexing.adapter.rubicon.model.RubiconVideoParams;
import org.rtb.vexing.model.AdUnitBid;
import org.rtb.vexing.model.BidResult;
import org.rtb.vexing.model.Bidder;
import org.rtb.vexing.model.BidderResult;
import org.rtb.vexing.model.MediaType;
import org.rtb.vexing.model.PreBidRequestContext;
import org.rtb.vexing.model.request.Sdk;
import org.rtb.vexing.model.response.Bid;
import org.rtb.vexing.model.response.BidderDebug;
import org.rtb.vexing.model.response.BidderStatus;
import org.rtb.vexing.model.response.UsersyncInfo;

import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Clock;
import java.util.Base64;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RubiconAdapter implements Adapter {

    private static final Logger logger = LoggerFactory.getLogger(RubiconAdapter.class);

    // RubiconParams and UsersyncInfo fields are not in snake-case
    private static final ObjectMapper DEFAULT_NAMING_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String APPLICATION_JSON =
            HttpHeaderValues.APPLICATION_JSON.toString() + ";" + HttpHeaderValues.CHARSET.toString() + "=" + "utf-8";

    private static final Set<MediaType> ALLOWED_MEDIA_TYPES = Collections.unmodifiableSet(
            EnumSet.of(MediaType.BANNER, MediaType.VIDEO));

    private static final String PREBID_SERVER_USER_AGENT = "prebid-server/1.0";

    private final String endpointUrl;
    private final UsersyncInfo usersyncInfo;
    private final String authHeader;
    private final HttpClient httpClient;

    private Clock clock = Clock.systemDefaultZone();

    public RubiconAdapter(String endpoint, String usersyncUrl, String xapiUsername, String xapiPassword,
                          HttpClient httpClient) {
        endpointUrl = validateUrl(Objects.requireNonNull(endpoint));
        usersyncInfo = UsersyncInfo.builder()
                .url(Objects.requireNonNull(usersyncUrl))
                .type("redirect")
                .supportCORS(false)
                .build();
        authHeader = "Basic " + Base64.getEncoder().encodeToString(
                (Objects.requireNonNull(xapiUsername) + ':' + Objects.requireNonNull(xapiPassword)).getBytes());

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

        Future<BidderResult> result = null;

        final long bidderStarted = clock.millis();

        List<BidWithRequest> bidWithRequests = Collections.emptyList();
        try {
            bidWithRequests = bidder.adUnitBids.stream()
                    .flatMap(adUnitBid -> toBidWithRequestStream(adUnitBid, preBidRequestContext))
                    .collect(Collectors.toList());
            validateBidWithRequests(bidWithRequests);
        } catch (PreBidRequestException e) {
            logger.warn("Error occurred while constructing bid requests", e);
            result = Future.succeededFuture(BidderResult.builder()
                    .bidderStatus(BidderStatus.builder()
                            .error(e.getMessage())
                            .responseTimeMs(responseTime(bidderStarted))
                            .build())
                    .bids(Collections.emptyList())
                    .build());
        }

        if (result == null) {
            final List<Future> requestBidFutures = bidWithRequests.stream()
                    .map(bidWithRequest -> requestSingleBid(bidWithRequest.bidRequest, preBidRequestContext.timeout,
                            bidWithRequest.adUnitBid))
                    .collect(Collectors.toList());

            final Future<BidderResult> bidderResultFuture = Future.future();
            CompositeFuture.join(requestBidFutures).setHandler(requestBidsResult ->
                    bidderResultFuture.complete(toBidderResult(bidder, preBidRequestContext,
                            requestBidsResult.result().list(), bidderStarted)));
            result = bidderResultFuture;
        }

        return result;
    }

    private static void validateBidWithRequests(List<BidWithRequest> bidWithRequests) {
        if (bidWithRequests.size() == 0) {
            throw new PreBidRequestException("Invalid ad unit/imp");
        }
    }

    private Stream<BidWithRequest> toBidWithRequestStream(AdUnitBid adUnitBid,
                                                          PreBidRequestContext preBidRequestContext) {
        final RubiconParams rubiconParams = parseAndValidateRubiconParams(adUnitBid);
        return makeImps(adUnitBid, rubiconParams, preBidRequestContext)
                .map(imp -> BidRequest.builder()
                        .id(preBidRequestContext.preBidRequest.tid)
                        .app(makeApp(rubiconParams, preBidRequestContext))
                        .at(1)
                        .tmax(preBidRequestContext.timeout)
                        .imp(Collections.singletonList(imp))
                        .site(makeSite(rubiconParams, preBidRequestContext))
                        .device(makeDevice(preBidRequestContext))
                        .user(makeUser(rubiconParams, preBidRequestContext))
                        .source(makeSource(preBidRequestContext))
                        .build())
                .map(bidRequest -> BidWithRequest.of(adUnitBid, bidRequest));
    }

    private RubiconParams parseAndValidateRubiconParams(AdUnitBid adUnitBid) {
        if (adUnitBid.params == null) {
            throw new PreBidRequestException("Rubicon params section is missing");
        }

        final RubiconParams rubiconParams;
        try {
            rubiconParams = DEFAULT_NAMING_MAPPER.convertValue(adUnitBid.params, RubiconParams.class);
        } catch (IllegalArgumentException e) {
            // a weird way to pass parsing exception
            throw new PreBidRequestException(e.getMessage(), e.getCause());
        }

        if (rubiconParams.accountId == null || rubiconParams.accountId == 0) {
            throw new PreBidRequestException("Missing accountId param");
        } else if (rubiconParams.siteId == null || rubiconParams.siteId == 0) {
            throw new PreBidRequestException("Missing siteId param");
        } else if (rubiconParams.zoneId == null || rubiconParams.zoneId == 0) {
            throw new PreBidRequestException("Missing zoneId param");
        }

        return rubiconParams;
    }

    private static App makeApp(RubiconParams rubiconParams, PreBidRequestContext preBidRequestContext) {
        final App app = preBidRequestContext.preBidRequest.app;
        return app == null ? null : app.toBuilder()
                .publisher(makePublisher(rubiconParams))
                .ext(Json.mapper.valueToTree(makeSiteExt(rubiconParams)))
                .build();
    }

    private static Stream<Imp> makeImps(AdUnitBid adUnitBid, RubiconParams rubiconParams,
                                        PreBidRequestContext preBidRequestContext) {

        return allowedMediaTypes(adUnitBid).stream()
                .filter(mediaType -> isValidAdUnitBidMediaType(mediaType, adUnitBid))
                .map(mediaType -> impBuilderWithMedia(mediaType, adUnitBid, rubiconParams))
                .map(impBuilder -> impBuilder
                        .id(adUnitBid.adUnitCode)
                        .secure(preBidRequestContext.secure)
                        .instl(adUnitBid.instl)
                        .ext(Json.mapper.valueToTree(makeImpExt(rubiconParams, preBidRequestContext)))
                        .build());
    }

    private static boolean isValidAdUnitBidMediaType(MediaType mediaType, AdUnitBid adUnitBid) {
        switch (mediaType) {
            case VIDEO:
                return adUnitBid.video != null && !CollectionUtils.isEmpty(adUnitBid.video.mimes);
            case BANNER:
                return adUnitBid.sizes.stream().map(RubiconSize::toId).anyMatch(id -> id > 0);
            default:
                return false;
        }
    }

    private static Imp.ImpBuilder impBuilderWithMedia(MediaType mediaType, AdUnitBid adUnitBid,
                                                      RubiconParams rubiconParams) {
        final Imp.ImpBuilder builder = Imp.builder();
        switch (mediaType) {
            case VIDEO:
                builder.video(makeVideo(adUnitBid, rubiconParams.video));
                break;
            case BANNER:
                builder.banner(makeBanner(adUnitBid));
                break;
            default:
                // unknown media type, just skip it
        }
        return builder;
    }

    private static Set<MediaType> allowedMediaTypes(AdUnitBid adUnitBid) {
        final Set<MediaType> allowedMediaTypes = new HashSet<>(ALLOWED_MEDIA_TYPES);
        allowedMediaTypes.retainAll(adUnitBid.mediaTypes);
        return allowedMediaTypes;
    }

    private static RubiconImpExt makeImpExt(RubiconParams rubiconParams, PreBidRequestContext preBidRequestContext) {
        return RubiconImpExt.builder()
                .rp(RubiconImpExtRp.builder()
                        .zoneId(rubiconParams.zoneId)
                        .target(!rubiconParams.inventory.isNull() ? rubiconParams.inventory : null)
                        .track(makeImpExtRpTrack(preBidRequestContext))
                        .build())
                .build();
    }

    private static RubiconImpExtRpTrack makeImpExtRpTrack(PreBidRequestContext preBidRequestContext) {
        final Sdk sdk = preBidRequestContext.preBidRequest.sdk;
        final String mintVersion;
        if (sdk != null) {
            mintVersion = String.format("%s_%s_%s", StringUtils.defaultString(sdk.source),
                    StringUtils.defaultString(sdk.platform), StringUtils.defaultString(sdk.version));
        } else {
            mintVersion = "__";
        }

        return RubiconImpExtRpTrack.builder()
                .mint("prebid")
                .mintVersion(mintVersion)
                .build();
    }

    private static Banner makeBanner(AdUnitBid adUnitBid) {
        return Banner.builder()
                .w(adUnitBid.sizes.get(0).getW())
                .h(adUnitBid.sizes.get(0).getH())
                .format(adUnitBid.sizes)
                .topframe(adUnitBid.topframe)
                .ext(Json.mapper.valueToTree(makeBannerExt(adUnitBid.sizes)))
                .build();
    }

    private static Video makeVideo(AdUnitBid adUnitBid, RubiconVideoParams rubiconVideoParams) {
        return Video.builder()
                .mimes(adUnitBid.video.mimes)
                .minduration(adUnitBid.video.minduration)
                .maxduration(adUnitBid.video.maxduration)
                .w(adUnitBid.sizes.get(0).getW())
                .h(adUnitBid.sizes.get(0).getH())
                .startdelay(adUnitBid.video.startdelay)
                .playbackmethod(Collections.singletonList(adUnitBid.video.playbackMethod))
                .protocols(adUnitBid.video.protocols)
                .ext(rubiconVideoParams != null ? Json.mapper.valueToTree(makeVideoExt(rubiconVideoParams)) : null)
                .build();
    }

    private static RubiconVideoExt makeVideoExt(RubiconVideoParams rubiconVideoParams) {
        return RubiconVideoExt.builder()
                .skip(rubiconVideoParams.skip)
                .skipdelay(rubiconVideoParams.skipdelay)
                .rp(RubiconVideoExtRP.builder()
                        .sizeId(rubiconVideoParams.sizeId)
                        .build())
                .build();
    }

    private static RubiconBannerExt makeBannerExt(List<Format> sizes) {
        final List<Integer> validRubiconSizeIds = sizes.stream()
                .map(RubiconSize::toId)
                .filter(id -> id > 0)
                .collect(Collectors.toList());
        return RubiconBannerExt.builder()
                .rp(RubiconBannerExtRp.builder()
                        .sizeId(validRubiconSizeIds.get(0))
                        .altSizeIds(validRubiconSizeIds.size() > 1
                                ? validRubiconSizeIds.subList(1, validRubiconSizeIds.size()) : null)
                        .mime("text/html")
                        .build())
                .build();
    }

    private Site makeSite(RubiconParams rubiconParams, PreBidRequestContext preBidRequestContext) {
        final Site.SiteBuilder siteBuilder = Site.builder();

        if (preBidRequestContext.preBidRequest.app != null) {
            final User user = preBidRequestContext.preBidRequest.user;
            final String language = user != null ? user.getLanguage() : null;
            siteBuilder
                    .content(Content.builder().language(language).build());
        } else {
            siteBuilder
                    .domain(preBidRequestContext.domain)
                    .page(preBidRequestContext.referer)
                    .publisher(makePublisher(rubiconParams))
                    .ext(Json.mapper.valueToTree(makeSiteExt(rubiconParams)));
        }

        return siteBuilder.build();
    }

    private static RubiconSiteExt makeSiteExt(RubiconParams rubiconParams) {
        return RubiconSiteExt.builder()
                .rp(RubiconSiteExtRp.builder().siteId(rubiconParams.siteId).build())
                .build();
    }

    private static Publisher makePublisher(RubiconParams rubiconParams) {
        return Publisher.builder()
                .ext(Json.mapper.valueToTree(makePublisherExt(rubiconParams)))
                .build();
    }

    private static RubiconPubExt makePublisherExt(RubiconParams rubiconParams) {
        return RubiconPubExt.builder()
                .rp(RubiconPubExtRp.builder().accountId(rubiconParams.accountId).build())
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
                .ext(Json.mapper.valueToTree(makeDeviceExt(preBidRequestContext)))
                .build();
    }

    private static RubiconDeviceExt makeDeviceExt(PreBidRequestContext preBidRequestContext) {
        final Device device = preBidRequestContext.preBidRequest.device;
        final BigDecimal pixelratio = device != null ? device.getPxratio() : null;

        return RubiconDeviceExt.builder()
                .rp(RubiconDeviceExtRp.builder().pixelratio(pixelratio).build())
                .build();
    }

    private User makeUser(RubiconParams rubiconParams, PreBidRequestContext preBidRequestContext) {
        // create a copy since user might be shared with other adapters
        final User user = preBidRequestContext.preBidRequest.user;
        final User.UserBuilder userBuilder = user != null ? user.toBuilder() : User.builder();

        if (preBidRequestContext.preBidRequest.app == null) {
            userBuilder
                    .buyeruid(preBidRequestContext.uidsCookie.uidFrom(cookieFamily()))
                    // id is a UID for "adnxs" (see logic in open-source implementation)
                    .id(preBidRequestContext.uidsCookie.uidFrom("adnxs"));
        }

        return userBuilder
                .ext(Json.mapper.valueToTree(makeUserExt(rubiconParams, preBidRequestContext)))
                .build();
    }

    private static RubiconUserExt makeUserExt(RubiconParams rubiconParams, PreBidRequestContext preBidRequestContext) {

        if (rubiconParams.visitor.isNull() && preBidRequestContext.preBidRequest.digiTrust == null) {
            return null;
        }

        final RubiconUserExt.RubiconUserExtBuilder rubiconUserExtBuilder = RubiconUserExt.builder();

        if (!rubiconParams.visitor.isNull()) {
            rubiconUserExtBuilder.rp(RubiconUserExtRp.builder()
                    .target(rubiconParams.visitor).build());
        }

        // add DigiTrust id to Bid request only in case of preference equals to 0
        if (preBidRequestContext.preBidRequest.digiTrust != null
                && preBidRequestContext.preBidRequest.digiTrust.pref == 0) {
            rubiconUserExtBuilder.dt(RubiconUserExtDt.builder()
                    .id(preBidRequestContext.preBidRequest.digiTrust.id)
                    .keyv(preBidRequestContext.preBidRequest.digiTrust.keyv)
                    .preference(preBidRequestContext.preBidRequest.digiTrust.pref)
                    .build());
        }

        return rubiconUserExtBuilder.build();
    }

    private static Source makeSource(PreBidRequestContext preBidRequestContext) {
        final Source.SourceBuilder sourceBuilder = Source.builder();

        if (preBidRequestContext.preBidRequest.app == null) {
            sourceBuilder.fd(1); // upstream, aka header
        }

        return sourceBuilder
                .tid(preBidRequestContext.preBidRequest.tid)
                .build();
    }

    private Future<BidResult> requestSingleBid(BidRequest bidRequest, long timeout, AdUnitBid adUnitBid) {
        final String bidRequestBody = Json.encode(bidRequest);

        final BidderDebug.BidderDebugBuilder bidderDebugBuilder = beginBidderDebug(bidRequestBody);
        final MediaType mediaType = bidRequest.getImp().get(0).getVideo() != null ? MediaType.VIDEO : MediaType.BANNER;
        final Future<BidResult> future = Future.future();
        httpClient.postAbs(endpointUrl,
                response -> handleResponse(response, adUnitBid, mediaType, bidderDebugBuilder, future))
                .exceptionHandler(exception -> handleException(exception, bidderDebugBuilder, future))
                .putHeader(HttpHeaders.AUTHORIZATION, authHeader)
                .putHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                .putHeader(HttpHeaders.ACCEPT, HttpHeaderValues.APPLICATION_JSON)
                .putHeader(HttpHeaders.USER_AGENT, PREBID_SERVER_USER_AGENT)
                .setTimeout(timeout)
                .end(bidRequestBody);
        return future;
    }

    private void handleResponse(HttpClientResponse response, AdUnitBid adUnitBid, MediaType mediaType,
                                BidderDebug.BidderDebugBuilder bidderDebugBuilder, Future<BidResult> future) {
        response
                .bodyHandler(buffer -> future.complete(toBidResult(adUnitBid, mediaType, bidderDebugBuilder,
                        response.statusCode(), buffer.toString())))
                .exceptionHandler(exception -> handleException(exception, bidderDebugBuilder, future));
    }

    private void handleException(Throwable exception,
                                 BidderDebug.BidderDebugBuilder bidderDebugBuilder,
                                 Future<BidResult> future) {
        logger.warn("Error occurred while sending bid request to an exchange", exception);
        final BidderDebug bidderDebug = bidderDebugBuilder.build();
        future.complete(exception instanceof TimeoutException || exception instanceof ConnectTimeoutException
                ? BidResult.timeout(bidderDebug, "Timed out")
                : BidResult.error(bidderDebug, exception.getMessage()));
    }

    private BidResult toBidResult(AdUnitBid adUnitBid, MediaType mediaType,
                                  BidderDebug.BidderDebugBuilder bidderDebugBuilder, int statusCode, String body) {
        final BidderDebug bidderDebug = completeBidderDebug(bidderDebugBuilder, statusCode, body);

        final BidResult result;

        if (statusCode == 204) {
            result = BidResult.empty(bidderDebug);
        } else if (statusCode != 200) {
            logger.warn("Bid response code is {0}, body: {1}", statusCode, body);
            result = BidResult.error(bidderDebug, String.format("HTTP status %d; body: %s", statusCode, body));
        } else {
            result = processBidResponse(adUnitBid, mediaType, body, bidderDebug);
        }

        return result;
    }

    private BidResult processBidResponse(AdUnitBid adUnitBid, MediaType mediaType, String body,
                                         BidderDebug bidderDebug) {
        BidResult result = null;

        BidResponse bidResponse = null;
        try {
            bidResponse = Json.decodeValue(body, BidResponse.class);
        } catch (DecodeException e) {
            logger.warn("Error occurred while parsing bid response: {0}", body, e);
            result = BidResult.error(bidderDebug, e.getMessage());
        }

        final com.iab.openrtb.response.Bid bid = bidResponse != null ? singleBidFrom(bidResponse) : null;

        if (bid != null) {
            // validate that impId matches expected ad unit code
            result = Objects.equals(bid.getImpid(), adUnitBid.adUnitCode)
                    ? BidResult.success(Collections.singletonList(toBidBuilder(bid, adUnitBid, mediaType)), bidderDebug)
                    : BidResult.error(bidderDebug, String.format("Unknown ad unit code '%s'", bid.getImpid()));
        } else if (result == null) {
            result = BidResult.empty(bidderDebug);
        }

        return result;
    }

    private static com.iab.openrtb.response.Bid singleBidFrom(BidResponse bidResponse) {
        return bidResponse.getSeatbid() != null && !bidResponse.getSeatbid().isEmpty()
                && bidResponse.getSeatbid().get(0).getBid() != null
                && !bidResponse.getSeatbid().get(0).getBid().isEmpty()
                ? bidResponse.getSeatbid().get(0).getBid().get(0)
                : null;
    }

    private BidderDebug.BidderDebugBuilder beginBidderDebug(String bidRequestBody) {
        return BidderDebug.builder()
                .requestUri(endpointUrl)
                .requestBody(bidRequestBody);
    }

    private static BidderDebug completeBidderDebug(BidderDebug.BidderDebugBuilder bidderDebugBuilder,
                                                   int statusCode, String body) {
        return bidderDebugBuilder.responseBody(body).statusCode(statusCode).build();
    }

    private static Bid.BidBuilder toBidBuilder(com.iab.openrtb.response.Bid bid, AdUnitBid adUnitBid,
                                               MediaType mediaType) {
        return Bid.builder()
                .code(bid.getImpid())
                .price(bid.getPrice())
                .adm(bid.getAdm())
                .creativeId(bid.getCrid())
                .mediaType(mediaType.name().toLowerCase())
                .width(bid.getW())
                .height(bid.getH())
                .dealId(bid.getDealid())
                .adServerTargeting(toAdServerTargetingOrNull(bid))
                .bidder(adUnitBid.bidderCode)
                .bidId(adUnitBid.bidId);
    }

    private static Map<String, String> toAdServerTargetingOrNull(com.iab.openrtb.response.Bid bid) {
        RubiconTargetingExt rubiconTargetingExt = null;
        try {
            rubiconTargetingExt = Json.mapper.convertValue(bid.getExt(), RubiconTargetingExt.class);
        } catch (IllegalArgumentException e) {
            logger.warn("Exception occurred while de-serializing rubicon targeting extension", e);
        }

        return rubiconTargetingExt != null && rubiconTargetingExt.rp != null && rubiconTargetingExt.rp.targeting != null
                ? rubiconTargetingExt.rp.targeting.stream().collect(Collectors.toMap(t -> t.key, t -> t.values.get(0)))
                : null;
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
                .filter(bid -> bid.price.compareTo(BigDecimal.ZERO) != 0)
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

        if (preBidRequestContext.preBidRequest.app == null && preBidRequestContext.uidsCookie.uidFrom(cookieFamily())
                == null) {
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

    private int responseTime(long bidderStarted) {
        return Math.toIntExact(clock.millis() - bidderStarted);
    }

    private BidResult findBidResultWithError(List<BidResult> bidResults) {
        BidResult result = null;

        final ListIterator<BidResult> iterator = bidResults.listIterator(bidResults.size());
        while (iterator.hasPrevious()) {
            final BidResult current = iterator.previous();
            if (StringUtils.isNotBlank(current.error)) {
                result = current;
                break;
            }
        }

        return result;
    }

    @Override
    public String code() {
        return "rubicon";
    }

    @Override
    public String cookieFamily() {
        return "rubicon";
    }

    @Override
    public ObjectNode usersyncInfo() {
        return DEFAULT_NAMING_MAPPER.valueToTree(usersyncInfo);
    }

    @AllArgsConstructor(staticName = "of")
    @FieldDefaults(makeFinal = true, level = AccessLevel.PUBLIC)
    private static class BidWithRequest {
        AdUnitBid adUnitBid;
        BidRequest bidRequest;
    }
}
