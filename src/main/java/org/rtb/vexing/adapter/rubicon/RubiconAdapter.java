package org.rtb.vexing.adapter.rubicon;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.BidResponse;
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
import org.apache.commons.lang3.StringUtils;
import org.rtb.vexing.adapter.Adapter;
import org.rtb.vexing.adapter.PreBidRequestException;
import org.rtb.vexing.adapter.rubicon.model.RubiconBannerExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconBannerExtRp;
import org.rtb.vexing.adapter.rubicon.model.RubiconImpExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconImpExtRp;
import org.rtb.vexing.adapter.rubicon.model.RubiconParams;
import org.rtb.vexing.adapter.rubicon.model.RubiconPubExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconPubExtRp;
import org.rtb.vexing.adapter.rubicon.model.RubiconSiteExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconSiteExtRp;
import org.rtb.vexing.adapter.rubicon.model.RubiconTargetingExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconUserExt;
import org.rtb.vexing.adapter.rubicon.model.RubiconUserExtRp;
import org.rtb.vexing.model.AdUnitBid;
import org.rtb.vexing.model.BidResult;
import org.rtb.vexing.model.Bidder;
import org.rtb.vexing.model.BidderResult;
import org.rtb.vexing.model.PreBidRequestContext;
import org.rtb.vexing.model.request.PreBidRequest;
import org.rtb.vexing.model.response.Bid;
import org.rtb.vexing.model.response.BidderDebug;
import org.rtb.vexing.model.response.BidderStatus;
import org.rtb.vexing.model.response.UsersyncInfo;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Clock;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class RubiconAdapter implements Adapter {

    private static final Logger logger = LoggerFactory.getLogger(RubiconAdapter.class);

    // RubiconParams and UsersyncInfo fields are not in snake-case
    private static final ObjectMapper DEFAULT_NAMING_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final String APPLICATION_JSON =
            HttpHeaderValues.APPLICATION_JSON.toString() + ";" + HttpHeaderValues.CHARSET.toString() + "=" + "utf-8";
    private static final String PREBID_SERVER_USER_AGENT = "prebid-server/1.0";

    private final String endpoint;
    private final URL endpointUrl;
    private final UsersyncInfo usersyncInfo;
    private final String authHeader;

    private final HttpClient httpClient;

    private Clock clock = Clock.systemDefaultZone();

    public RubiconAdapter(String endpoint, String usersyncUrl, String xapiUsername, String xapiPassword,
                          HttpClient httpClient) {
        this.endpoint = Objects.requireNonNull(endpoint);
        endpointUrl = parseUrl(this.endpoint);
        usersyncInfo = UsersyncInfo.builder()
                .url(Objects.requireNonNull(usersyncUrl))
                .type("redirect")
                .supportCORS(false)
                .build();
        authHeader = "Basic " + Base64.getEncoder().encodeToString(
                (Objects.requireNonNull(xapiUsername) + ':' + Objects.requireNonNull(xapiPassword)).getBytes());

        this.httpClient = Objects.requireNonNull(httpClient);
    }

    private static URL parseUrl(String url) {
        try {
            return new URL(url);
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

        List<BidWithRequest> bidWithRequests = null;
        try {
            bidWithRequests = bidder.adUnitBids.stream()
                    .map(adUnitBid -> BidWithRequest.of(adUnitBid, toBidRequest(adUnitBid, preBidRequestContext)))
                    .collect(Collectors.toList());
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

        if (bidWithRequests != null) {
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

    private BidRequest toBidRequest(AdUnitBid adUnitBid, PreBidRequestContext preBidRequestContext) {
        final RubiconParams rubiconParams = parseAndValidateRubiconParams(adUnitBid);

        return BidRequest.builder()
                .id(preBidRequestContext.preBidRequest.tid)
                .app(preBidRequestContext.preBidRequest.app)
                .at(1)
                .tmax(preBidRequestContext.timeout)
                .imp(Collections.singletonList(makeImp(adUnitBid, rubiconParams, preBidRequestContext)))
                .site(makeSite(rubiconParams, preBidRequestContext))
                .device(makeDevice(preBidRequestContext))
                .user(makeUser(rubiconParams, preBidRequestContext))
                .source(makeSource(preBidRequestContext.preBidRequest))
                .build();
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

    private static Imp makeImp(AdUnitBid adUnitBid, RubiconParams rubiconParams, PreBidRequestContext
            preBidRequestContext) {
        return Imp.builder()
                .id(adUnitBid.adUnitCode)
                .secure(preBidRequestContext.secure)
                .instl(adUnitBid.instl)
                .banner(makeBanner(adUnitBid))
                .ext(Json.mapper.valueToTree(makeImpExt(rubiconParams)))
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

    private static RubiconBannerExt makeBannerExt(List<Format> sizes) {
        final List<Integer> rubiconSizeIds = sizes.stream()
                .map(RubiconSize::toId)
                .filter(id -> id > 0)
                .collect(Collectors.toList());

        if (rubiconSizeIds.isEmpty()) {
            throw new PreBidRequestException("No valid sizes");
        }

        return RubiconBannerExt.builder()
                .rp(RubiconBannerExtRp.builder()
                        .sizeId(rubiconSizeIds.get(0))
                        .altSizeIds(rubiconSizeIds.size() > 1
                                ? rubiconSizeIds.subList(1, rubiconSizeIds.size()) : null)
                        .mime("text/html")
                        .build())
                .build();
    }

    private static RubiconImpExt makeImpExt(RubiconParams rubiconParams) {
        return RubiconImpExt.builder()
                .rp(RubiconImpExtRp.builder()
                        .zoneId(rubiconParams.zoneId)
                        .target(!rubiconParams.inventory.isNull() ? rubiconParams.inventory : null)
                        .build())
                .build();
    }

    private Site makeSite(RubiconParams rubiconParams, PreBidRequestContext preBidRequestContext) {
        return Site.builder()
                .domain(preBidRequestContext.domain)
                .page(preBidRequestContext.referer)
                .publisher(makePublisher(rubiconParams))
                .ext(Json.mapper.valueToTree(makeSiteExt(rubiconParams)))
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

    private static RubiconSiteExt makeSiteExt(RubiconParams rubiconParams) {
        return RubiconSiteExt.builder()
                .rp(RubiconSiteExtRp.builder().siteId(rubiconParams.siteId).build())
                .build();
    }

    private static Device makeDevice(PreBidRequestContext preBidRequestContext) {
        return Device.builder()
                .ua(preBidRequestContext.ua)
                .ip(preBidRequestContext.ip)
                .build();
    }

    private User makeUser(RubiconParams rubiconParams, PreBidRequestContext preBidRequestContext) {
        // create a copy since user might be shared with other adapters
        final User.UserBuilder userBuilder =
                preBidRequestContext.preBidRequest.app != null ? preBidRequestContext.preBidRequest.user.toBuilder()
                        : User.builder()
                        .buyeruid(preBidRequestContext.uidsCookie.uidFrom(familyName()))
                        // id is a UID for "adnxs" (see logic in open-source implementation)
                        .id(preBidRequestContext.uidsCookie.uidFrom("adnxs"));

        return userBuilder
                .ext(Json.mapper.valueToTree(makeUserExt(rubiconParams)))
                .build();
    }

    private static RubiconUserExt makeUserExt(RubiconParams rubiconParams) {
        return !rubiconParams.visitor.isNull() ? RubiconUserExt.builder()
                .rp(RubiconUserExtRp.builder().target(rubiconParams.visitor).build())
                .build()
                : null;
    }

    private static Source makeSource(PreBidRequest preBidRequest) {
        return Source.builder()
                .fd(1)
                .tid(preBidRequest.tid)
                .build();
    }

    private Future<BidResult> requestSingleBid(BidRequest bidRequest, long timeout, AdUnitBid adUnitBid) {
        // FIXME: remove
        logger.debug("Bid request is {0}", Json.encodePrettily(bidRequest));

        final String bidRequestBody = Json.encode(bidRequest);

        final BidderDebug.BidderDebugBuilder bidderDebugBuilder = beginBidderDebug(bidRequestBody);

        final Future<BidResult> future = Future.future();
        httpClient.post(portFromUrl(endpointUrl), endpointUrl.getHost(), endpointUrl.getFile(),
                response -> handleResponse(response, adUnitBid, bidderDebugBuilder, future))
                .exceptionHandler(exception -> handleException(exception, bidderDebugBuilder, future))
                .putHeader(HttpHeaders.AUTHORIZATION, authHeader)
                .putHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                .putHeader(HttpHeaders.ACCEPT, HttpHeaderValues.APPLICATION_JSON)
                .putHeader(HttpHeaders.USER_AGENT, PREBID_SERVER_USER_AGENT)
                .setTimeout(timeout)
                .end(bidRequestBody);
        return future;
    }

    private static int portFromUrl(URL url) {
        final int port = url.getPort();
        return port != -1 ? port : url.getDefaultPort();
    }

    private void handleResponse(HttpClientResponse response, AdUnitBid adUnitBid,
                                BidderDebug.BidderDebugBuilder bidderDebugBuilder,
                                Future<BidResult> future) {
        response
                .bodyHandler(buffer -> future.complete(toBidResult(adUnitBid, bidderDebugBuilder, response.statusCode(),
                        buffer.toString())))
                .exceptionHandler(exception -> handleException(exception, bidderDebugBuilder, future));
    }

    private void handleException(Throwable exception,
                                 BidderDebug.BidderDebugBuilder bidderDebugBuilder,
                                 Future<BidResult> future) {
        logger.warn("Error occurred while sending bid request to an exchange", exception);
        final BidderDebug bidderDebug = bidderDebugBuilder.build();
        future.complete(exception instanceof TimeoutException
                ? BidResult.timeout(bidderDebug, "Timed out")
                : BidResult.error(bidderDebug, exception.getMessage()));
    }

    private BidResult toBidResult(AdUnitBid adUnitBid, BidderDebug.BidderDebugBuilder bidderDebugBuilder,
                                  int statusCode, String body) {
        final BidderDebug bidderDebug = completeBidderDebug(bidderDebugBuilder, statusCode, body);

        final BidResult result;

        if (statusCode == 204) {
            result = BidResult.empty(bidderDebug);
        } else if (statusCode != 200) {
            logger.warn("Bid response code is {0}, body: {1}", statusCode, body);
            result = BidResult.error(bidderDebug, String.format("HTTP status %d; body: %s", statusCode, body));
        } else {
            result = processBidResponse(adUnitBid, body, bidderDebug);
        }

        return result;
    }

    private BidResult processBidResponse(AdUnitBid adUnitBid, String body, BidderDebug bidderDebug) {
        // FIXME: remove
        logger.debug("Bid response body raw: {0}", body);
        try {
            logger.debug("Bid response: {0}",
                    Json.encodePrettily(Json.decodeValue(body, BidResponse.class)));
        } catch (DecodeException e) {
            // do nothing
        }

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
                    ? BidResult.success(toBidBuilder(bid, adUnitBid), bidderDebug)
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
                .requestUri(endpoint)
                .requestBody(bidRequestBody);
    }

    private static BidderDebug completeBidderDebug(BidderDebug.BidderDebugBuilder bidderDebugBuilder,
                                                   int statusCode, String body) {
        return bidderDebugBuilder.responseBody(body).statusCode(statusCode).build();
    }

    private static Bid.BidBuilder toBidBuilder(com.iab.openrtb.response.Bid bid, AdUnitBid adUnitBid) {
        return Bid.builder()
                .code(bid.getImpid())
                .price(bid.getPrice()) // FIXME: now 0 is serialized as "0.0", but should be just "0"
                .adm(bid.getAdm())
                .creativeId(bid.getCrid())
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
                .map(br -> br.bidBuilder)
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

        if (preBidRequestContext.preBidRequest.app == null && preBidRequestContext.uidsCookie.uidFrom(familyName())
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
    public String familyName() {
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
