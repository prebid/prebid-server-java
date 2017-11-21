package org.rtb.vexing.handler;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.rtb.vexing.adapter.Adapter;
import org.rtb.vexing.adapter.AdapterCatalog;
import org.rtb.vexing.adapter.PreBidRequestContextFactory;
import org.rtb.vexing.adapter.PreBidRequestException;
import org.rtb.vexing.cache.CacheService;
import org.rtb.vexing.cache.model.BidCacheResult;
import org.rtb.vexing.metric.AccountMetrics;
import org.rtb.vexing.metric.AdapterMetrics;
import org.rtb.vexing.metric.MetricName;
import org.rtb.vexing.metric.Metrics;
import org.rtb.vexing.model.Bidder;
import org.rtb.vexing.model.BidderResult;
import org.rtb.vexing.model.PreBidRequestContext;
import org.rtb.vexing.model.request.PreBidRequest;
import org.rtb.vexing.model.response.Bid;
import org.rtb.vexing.model.response.BidderStatus;
import org.rtb.vexing.model.response.PreBidResponse;
import org.rtb.vexing.settings.ApplicationSettings;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class AuctionHandler {

    private static final Logger logger = LoggerFactory.getLogger(AuctionHandler.class);

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    private final ApplicationSettings applicationSettings;
    private final AdapterCatalog adapters;
    private final PreBidRequestContextFactory preBidRequestContextFactory;
    private final CacheService cacheService;
    private final Metrics metrics;

    private String date;
    private final Clock clock = Clock.systemDefaultZone();

    public AuctionHandler(ApplicationSettings applicationSettings, AdapterCatalog adapters,
                          PreBidRequestContextFactory preBidRequestContextFactory, CacheService cacheService,
                          Vertx vertx, Metrics metrics) {
        this.applicationSettings = Objects.requireNonNull(applicationSettings);
        this.adapters = Objects.requireNonNull(adapters);
        this.preBidRequestContextFactory = Objects.requireNonNull(preBidRequestContextFactory);
        this.cacheService = Objects.requireNonNull(cacheService);
        this.metrics = Objects.requireNonNull(metrics);

        // Refresh the date included in the response header every second.
        final Handler<Long> dateUpdater = event -> date = DateTimeFormatter.RFC_1123_DATE_TIME.format(
                ZonedDateTime.now());
        dateUpdater.handle(0L);
        Objects.requireNonNull(vertx).setPeriodic(1000, dateUpdater);
    }

    /**
     * Auction handler will resolve all bidders in the incoming ad request, issue the request to the different
     * clients, then return an array of the responses.
     */
    public void auction(RoutingContext context) {
        metrics.incCounter(MetricName.requests);

        final boolean isSafari = isSafari(context.request().headers().get(HttpHeaders.USER_AGENT));
        if (isSafari) {
            metrics.incCounter(MetricName.safari_requests);
        }

        // parse and validate request and headers
        final PreBidRequestContext preBidRequestContext;
        try {
            preBidRequestContext = preBidRequestContextFactory.fromRequest(context);
        } catch (PreBidRequestException e) {
            logger.info("Failed to parse /auction request", e);
            respondWith(error(String.format("Error parsing request: %s", e.getMessage())), context);
            metrics.incCounter(MetricName.error_requests);
            return;
        }

        updateAppAndNoCookieMetrics(preBidRequestContext, isSafari);

        // validate account id
        final String accountId = preBidRequestContext.preBidRequest.accountId;
        if (!applicationSettings.getAccountById(accountId).isPresent()) {
            logger.info("Invalid account id: Not found");
            respondWith(error("Unknown account id: Unknown account"), context);
            metrics.incCounter(MetricName.error_requests);
            return;
        }
        metrics.forAccount(accountId).incCounter(MetricName.requests);

        // set up handler to update request time metric when response is sent back to a client
        final long requestStarted = clock.millis();
        context.response().endHandler(ignored -> metrics.updateTimer(MetricName.request_time,
                clock.millis() - requestStarted));

        // submit request to relevant adapters and build response when all bids are received
        final List<Future> bidderResponseFutures = preBidRequestContext.bidders.stream()
                .filter(AuctionHandler::isValidBidder)
                .peek(bidder -> updateAdapterRequestMetrics(bidder.bidderCode, accountId))
                .map(bidder -> adapters.get(bidder.bidderCode).requestBids(bidder, preBidRequestContext))
                .collect(Collectors.toList());

        CompositeFuture.join(bidderResponseFutures)
                .compose(bidderResponsesResult ->
                        composePreBidResponse(preBidRequestContext, bidderResponsesResult))
                .compose(preBidResponse -> processCacheMarkup(preBidRequestContext.preBidRequest, preBidResponse))
                .setHandler(preBidResponseResult -> respondWith(bidResponseOrError(preBidResponseResult), context));
    }

    private Future<PreBidResponse> composePreBidResponse(PreBidRequestContext preBidRequestContext,
                                                         CompositeFuture bidderResponsesResult) {
        final List<BidderResult> bidderResults = bidderResponsesResult.result().list();

        bidderResults.stream()
                .filter(br -> StringUtils.isNotBlank(br.bidderStatus.error))
                .forEach(br -> updateErrorMetrics(br, preBidRequestContext));

        final List<BidderStatus> bidderStatuses = Stream.concat(
                bidderResults.stream()
                        .map(br -> br.bidderStatus)
                        .peek(bs -> updateResponseTimeMetrics(bs, preBidRequestContext)),
                invalidBidderStatuses(preBidRequestContext))
                .collect(Collectors.toList());

        final List<Bid> bids = bidderResults.stream()
                .filter(br -> StringUtils.isBlank(br.bidderStatus.error))
                .peek(br -> updateBidResultMetrics(br, preBidRequestContext))
                .flatMap(br -> br.bids.stream())
                .collect(Collectors.toList());

        final PreBidResponse response = PreBidResponse.builder()
                .status(preBidRequestContext.uidsCookie.hasLiveUids() ? "OK" : "no_cookie")
                .tid(preBidRequestContext.preBidRequest.tid)
                .bidderStatus(bidderStatuses)
                .bids(bids)
                .build();

        return Future.succeededFuture(response);
    }

    private Future<PreBidResponse> processCacheMarkup(PreBidRequest preBidRequest, PreBidResponse preBidResponse) {
        final List<Bid> bids = preBidResponse.bids;
        if (preBidRequest.cacheMarkup != null && preBidRequest.cacheMarkup == 1 && !bids.isEmpty()) {
            return cacheService.saveBids(bids)
                    .compose(bidCacheResults -> {
                        final List<Bid> bidsWithCacheUUIDs = IntStream.range(0, bids.size())
                                .mapToObj(i -> {
                                    BidCacheResult result = bidCacheResults.get(i);
                                    return bids.get(i).toBuilder()
                                            .adm(null)
                                            .nurl(null)
                                            .cacheId(result.cacheId)
                                            .cacheUrl(result.cacheUrl)
                                            .build();
                                })
                                .collect(Collectors.toList());

                        final PreBidResponse response = preBidResponse.toBuilder().bids(bidsWithCacheUUIDs).build();

                        return Future.succeededFuture(response);
                    });
        }
        return Future.succeededFuture(preBidResponse);
    }

    private void respondWith(PreBidResponse response, RoutingContext context) {
        context.response()
                .putHeader(HttpHeaders.DATE, date)
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                .end(Json.encode(response));
    }

    private static PreBidResponse bidResponseOrError(AsyncResult<PreBidResponse> responseResult) {
        if (responseResult.succeeded()) {
            return responseResult.result();
        } else {
            logger.warn("Unexpected server error occurred", responseResult.cause());
            return error("Unexpected server error");
        }
    }

    private static PreBidResponse error(String status) {
        return PreBidResponse.builder().status(status).build();
    }

    private static boolean isValidBidder(Bidder bidder) {
        try {
            Adapter.Type.valueOf(bidder.bidderCode);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static Stream<BidderStatus> invalidBidderStatuses(PreBidRequestContext preBidRequestContext) {
        return preBidRequestContext.bidders.stream()
                .filter(b -> !isValidBidder(b))
                .map(b -> BidderStatus.builder().bidder(b.bidderCode).error("Unsupported bidder").build());
    }

    private static boolean isSafari(String userAgent) {
        // this is a simple heuristic based on this article:
        // https://developer.mozilla.org/en-US/docs/Web/HTTP/Browser_detection_using_the_user_agent
        //
        // there are libraries available doing different kinds of User-Agent analysis but they impose performance
        // implications as well, example: https://github.com/nielsbasjes/yauaa
        return StringUtils.isNotBlank(userAgent) && userAgent.contains("AppleWebKit") && userAgent.contains("Safari")
                && !userAgent.contains("Chrome") && !userAgent.contains("Chromium");
    }

    private void updateAppAndNoCookieMetrics(PreBidRequestContext preBidRequestContext, boolean isSafari) {
        if (preBidRequestContext.preBidRequest.app != null) {
            metrics.incCounter(MetricName.app_requests);
        } else if (!preBidRequestContext.uidsCookie.hasLiveUids()) {
            metrics.incCounter(MetricName.no_cookie_requests);
            if (isSafari) {
                metrics.incCounter(MetricName.safari_no_cookie_requests);
            }
        }
    }

    private void updateAdapterRequestMetrics(String bidderCode, String accountId) {
        final Adapter.Type adapterType = Adapter.Type.valueOf(bidderCode);

        metrics.forAdapter(adapterType).incCounter(MetricName.requests);
        metrics.forAccount(accountId).forAdapter(adapterType).incCounter(MetricName.requests);
    }

    private void updateResponseTimeMetrics(BidderStatus bidderStatus, PreBidRequestContext preBidRequestContext) {
        final Adapter.Type adapterType = Adapter.Type.valueOf(bidderStatus.bidder);

        metrics.forAdapter(adapterType).updateTimer(MetricName.request_time, bidderStatus.responseTimeMs);
        metrics.forAccount(preBidRequestContext.preBidRequest.accountId).forAdapter(adapterType)
                .updateTimer(MetricName.request_time, bidderStatus.responseTimeMs);
    }

    private void updateBidResultMetrics(BidderResult bidderResult, PreBidRequestContext preBidRequestContext) {
        final BidderStatus bidderStatus = bidderResult.bidderStatus;
        final Adapter.Type adapterType = Adapter.Type.valueOf(bidderStatus.bidder);
        final AdapterMetrics adapterMetrics = metrics.forAdapter(adapterType);
        final AccountMetrics accountMetrics = metrics.forAccount(preBidRequestContext.preBidRequest.accountId);
        final AdapterMetrics accountAdapterMetrics = accountMetrics.forAdapter(adapterType);

        for (final Bid bid : bidderResult.bids) {
            final long cpm = bid.price.multiply(THOUSAND).longValue();
            adapterMetrics.updateHistogram(MetricName.prices, cpm);
            accountMetrics.updateHistogram(MetricName.prices, cpm);
            accountAdapterMetrics.updateHistogram(MetricName.prices, cpm);
        }

        if (bidderStatus.numBids != null) {
            accountMetrics.incCounter(MetricName.bids_received, bidderStatus.numBids);
            accountAdapterMetrics.incCounter(MetricName.bids_received, bidderStatus.numBids);
        } else if (Objects.equals(bidderStatus.noBid, Boolean.TRUE)) {
            adapterMetrics.incCounter(MetricName.no_bid_requests);
            accountAdapterMetrics.incCounter(MetricName.no_bid_requests);
        }

        if (Objects.equals(bidderStatus.noCookie, Boolean.TRUE)) {
            adapterMetrics.incCounter(MetricName.no_cookie_requests);
            accountAdapterMetrics.incCounter(MetricName.no_cookie_requests);
        }
    }

    private void updateErrorMetrics(BidderResult bidderResult, PreBidRequestContext preBidRequestContext) {
        final AdapterMetrics adapterMetrics = metrics
                .forAdapter(Adapter.Type.valueOf(bidderResult.bidderStatus.bidder));
        final AdapterMetrics accountAdapterMetrics = metrics.forAccount(preBidRequestContext.preBidRequest.accountId)
                .forAdapter(Adapter.Type.valueOf(bidderResult.bidderStatus.bidder));

        if (bidderResult.timedOut) {
            adapterMetrics.incCounter(MetricName.timeout_requests);
            accountAdapterMetrics.incCounter(MetricName.timeout_requests);
        } else {
            adapterMetrics.incCounter(MetricName.error_requests);
            accountAdapterMetrics.incCounter(MetricName.error_requests);
        }
    }
}
