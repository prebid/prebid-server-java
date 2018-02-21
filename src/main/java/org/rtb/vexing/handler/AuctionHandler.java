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
import org.rtb.vexing.adapter.AdapterCatalog;
import org.rtb.vexing.adapter.HttpConnector;
import org.rtb.vexing.auction.PreBidRequestContextFactory;
import org.rtb.vexing.auction.TargetingKeywordsCreator;
import org.rtb.vexing.cache.CacheService;
import org.rtb.vexing.cache.model.BidCacheResult;
import org.rtb.vexing.exception.PreBidException;
import org.rtb.vexing.metric.AccountMetrics;
import org.rtb.vexing.metric.AdapterMetrics;
import org.rtb.vexing.metric.MetricName;
import org.rtb.vexing.metric.Metrics;
import org.rtb.vexing.model.BidderResult;
import org.rtb.vexing.model.PreBidRequestContext;
import org.rtb.vexing.model.Tuple2;
import org.rtb.vexing.model.Tuple3;
import org.rtb.vexing.model.request.PreBidRequest;
import org.rtb.vexing.model.response.Bid;
import org.rtb.vexing.model.response.BidderStatus;
import org.rtb.vexing.model.response.PreBidResponse;
import org.rtb.vexing.settings.ApplicationSettings;
import org.rtb.vexing.settings.model.Account;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class AuctionHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(AuctionHandler.class);

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    private final ApplicationSettings applicationSettings;
    private final AdapterCatalog adapters;
    private final PreBidRequestContextFactory preBidRequestContextFactory;
    private final CacheService cacheService;
    private final Metrics metrics;
    private final HttpConnector httpConnector;

    private String date;
    private final Clock clock = Clock.systemDefaultZone();

    public AuctionHandler(ApplicationSettings applicationSettings, AdapterCatalog adapters,
                          PreBidRequestContextFactory preBidRequestContextFactory, CacheService cacheService,
                          Vertx vertx, Metrics metrics, HttpConnector httpConnector) {
        this.applicationSettings = Objects.requireNonNull(applicationSettings);
        this.adapters = Objects.requireNonNull(adapters);
        this.preBidRequestContextFactory = Objects.requireNonNull(preBidRequestContextFactory);
        this.cacheService = Objects.requireNonNull(cacheService);
        this.metrics = Objects.requireNonNull(metrics);
        this.httpConnector = Objects.requireNonNull(httpConnector);

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
    @Override
    public void handle(RoutingContext context) {
        metrics.incCounter(MetricName.requests);

        final boolean isSafari = isSafari(context.request().headers().get(HttpHeaders.USER_AGENT));
        if (isSafari) {
            metrics.incCounter(MetricName.safari_requests);
        }

        preBidRequestContextFactory.fromRequest(context)
                .recover(exception ->
                        failWith(String.format("Error parsing request: %s", exception.getMessage()), exception))
                .compose(preBidRequestContext -> {
                    updateAppAndNoCookieMetrics(preBidRequestContext, isSafari);

                    // validate account id
                    return applicationSettings.getAccountById(
                            preBidRequestContext.getPreBidRequest().getAccountId(), preBidRequestContext.getTimeout())
                            .compose(account -> Future.succeededFuture(Tuple2.of(preBidRequestContext, account)))
                            .recover(exception -> failWith("Unknown account id: Unknown account", exception));
                })
                .compose(result -> {
                    final PreBidRequestContext preBidRequestContext = result.getLeft();
                    final String accountId = preBidRequestContext.getPreBidRequest().getAccountId();

                    metrics.forAccount(accountId).incCounter(MetricName.requests);
                    setupRequestTimeUpdater(context);

                    return CompositeFuture.join(submitRequestsToAdapters(preBidRequestContext, accountId))
                            .map(bidderResults -> Tuple3.of(preBidRequestContext, result.getRight(),
                                    bidderResults.result().<BidderResult>list()));
                })
                .map(result -> Tuple3.of(result.getLeft(), result.getMiddle(),
                        composePreBidResponse(result.getLeft(), result.getRight())))
                .compose(result -> processCacheMarkup(result.getLeft(), result.getRight())
                        .recover(exception ->
                                failWith(String.format("Prebid cache failed: %s", exception.getMessage()), exception))
                        .map(response -> Tuple3.of(result.getLeft(), result.getMiddle(), response)))
                .map(result -> addTargetingKeywords(result.getLeft().getPreBidRequest(), result.getMiddle(),
                        result.getRight()))
                .setHandler(preBidResponseResult -> respondWith(bidResponseOrError(preBidResponseResult), context));
    }

    private static <T> Future<T> failWith(String message, Throwable exception) {
        return Future.failedFuture(new PreBidException(message, exception));
    }

    private List<Future> submitRequestsToAdapters(PreBidRequestContext preBidRequestContext, String accountId) {
        return preBidRequestContext.getBidders().stream()
                .filter(bidder -> adapters.isValidCode(bidder.getBidderCode()))
                .peek(bidder -> updateAdapterRequestMetrics(bidder.getBidderCode(), accountId))
                .map(bidder -> httpConnector.call(adapters.getByCode(bidder.getBidderCode()), bidder,
                        preBidRequestContext))
                .collect(Collectors.toList());
    }

    private PreBidResponse composePreBidResponse(PreBidRequestContext preBidRequestContext,
                                                 List<BidderResult> bidderResults) {
        bidderResults.stream()
                .filter(br -> StringUtils.isNotBlank(br.getBidderStatus().getError()))
                .forEach(br -> updateErrorMetrics(br, preBidRequestContext));

        final List<BidderStatus> bidderStatuses = Stream.concat(
                bidderResults.stream()
                        .map(BidderResult::getBidderStatus)
                        .peek(bs -> updateResponseTimeMetrics(bs, preBidRequestContext)),
                invalidBidderStatuses(preBidRequestContext))
                .collect(Collectors.toList());

        final List<Bid> bids = bidderResults.stream()
                .filter(br -> StringUtils.isBlank(br.getBidderStatus().getError()))
                .peek(br -> updateBidResultMetrics(br, preBidRequestContext))
                .flatMap(br -> br.getBids().stream())
                .collect(Collectors.toList());

        return PreBidResponse.builder()
                .status(preBidRequestContext.isNoLiveUids() ? "no_cookie" : "OK")
                .tid(preBidRequestContext.getPreBidRequest().getTid())
                .bidderStatus(bidderStatuses)
                .bids(bids)
                .build();
    }

    private Future<PreBidResponse> processCacheMarkup(PreBidRequestContext preBidRequestContext,
                                                      PreBidResponse preBidResponse) {
        final Future<PreBidResponse> result;

        final Integer cacheMarkup = preBidRequestContext.getPreBidRequest().getCacheMarkup();
        final List<Bid> bids = preBidResponse.getBids();
        if (cacheMarkup != null && cacheMarkup == 1 && !bids.isEmpty()) {
            result = cacheService.cacheBids(bids, preBidRequestContext.getTimeout())
                    .map(bidCacheResults -> mergeBidsWithCacheResults(preBidResponse, bidCacheResults));
        } else {
            result = Future.succeededFuture(preBidResponse);
        }

        return result;
    }

    private PreBidResponse mergeBidsWithCacheResults(PreBidResponse preBidResponse,
                                                     List<BidCacheResult> bidCacheResults) {
        final List<Bid> bids = preBidResponse.getBids();
        final List<Bid> bidsWithCacheUUIDs = IntStream.range(0, bids.size())
                .mapToObj(i -> {
                    BidCacheResult result = bidCacheResults.get(i);
                    return bids.get(i).toBuilder()
                            .adm(null)
                            .nurl(null)
                            .cacheId(result.getCacheId())
                            .cacheUrl(result.getCacheUrl())
                            .build();
                })
                .collect(Collectors.toList());

        return preBidResponse.toBuilder().bids(bidsWithCacheUUIDs).build();
    }

    /**
     * Sorts the bids and adds ad server targeting keywords to each bid.
     * The bids are sorted by cpm to find the highest bid.
     * The ad server targeting keywords are added to all bids, with specific keywords for the highest bid.
     */
    private static PreBidResponse addTargetingKeywords(PreBidRequest preBidRequest, Account account,
                                                       PreBidResponse preBidResponse) {
        PreBidResponse result = preBidResponse;

        final Integer sortBids = preBidRequest.getSortBids();
        if (sortBids != null && sortBids == 1) {
            final TargetingKeywordsCreator keywordsCreator =
                    TargetingKeywordsCreator.withPriceGranularity(account.getPriceGranularity());

            final List<Bid> bidsWithKeywords = preBidResponse.getBids().stream()
                    .collect(Collectors.groupingBy(Bid::getCode))
                    .values().stream()
                    .peek(bids -> bids.sort(Comparator.comparing(Bid::getPrice)
                            .reversed()
                            .thenComparing(Bid::getResponseTimeMs)))
                    .flatMap(bids -> bids.stream().map(bid -> bid.toBuilder().adServerTargeting(joinMaps(
                            keywordsCreator.makeFor(bid, bid == bids.get(0)), bid.getAdServerTargeting()))
                            .build()))
                    .collect(Collectors.toList());

            result = preBidResponse.toBuilder().bids(bidsWithKeywords).build();
        }

        return result;
    }

    private static <K, V> Map<K, V> joinMaps(Map<K, V> left, Map<K, V> right) {
        if (right != null) {
            left.putAll(right);
        }
        return left;
    }

    private void respondWith(PreBidResponse response, RoutingContext context) {
        context.response()
                .putHeader(HttpHeaders.DATE, date)
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                .end(Json.encode(response));
    }

    private PreBidResponse bidResponseOrError(AsyncResult<PreBidResponse> responseResult) {
        if (responseResult.succeeded()) {
            return responseResult.result();
        } else {
            metrics.incCounter(MetricName.error_requests);
            final Throwable exception = responseResult.cause();
            logger.info("Failed to process /auction request", exception);
            return error(exception instanceof PreBidException
                    ? exception.getMessage()
                    : "Unexpected server error");
        }
    }

    private static PreBidResponse error(String status) {
        return PreBidResponse.builder().status(status).build();
    }

    private Stream<BidderStatus> invalidBidderStatuses(PreBidRequestContext preBidRequestContext) {
        return preBidRequestContext.getBidders().stream()
                .filter(b -> !adapters.isValidCode(b.getBidderCode()))
                .map(b -> BidderStatus.builder().bidder(b.getBidderCode()).error("Unsupported bidder").build());
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
        if (preBidRequestContext.getPreBidRequest().getApp() != null) {
            metrics.incCounter(MetricName.app_requests);
        } else if (preBidRequestContext.isNoLiveUids()) {
            metrics.incCounter(MetricName.no_cookie_requests);
            if (isSafari) {
                metrics.incCounter(MetricName.safari_no_cookie_requests);
            }
        }
    }

    private void setupRequestTimeUpdater(RoutingContext context) {
        // set up handler to update request time metric when response is sent back to a client
        final long requestStarted = clock.millis();
        context.response().endHandler(ignoredVoid -> metrics.updateTimer(MetricName.request_time,
                clock.millis() - requestStarted));
    }

    private void updateAdapterRequestMetrics(String bidderCode, String accountId) {
        metrics.forAdapter(bidderCode).incCounter(MetricName.requests);
        metrics.forAccount(accountId).forAdapter(bidderCode).incCounter(MetricName.requests);
    }

    private void updateResponseTimeMetrics(BidderStatus bidderStatus, PreBidRequestContext preBidRequestContext) {
        final String bidder = bidderStatus.getBidder();
        final Integer responseTimeMs = bidderStatus.getResponseTimeMs();
        metrics.forAdapter(bidder).updateTimer(MetricName.request_time, responseTimeMs);
        metrics.forAccount(preBidRequestContext.getPreBidRequest().getAccountId()).forAdapter(bidder)
                .updateTimer(MetricName.request_time, responseTimeMs);
    }

    private void updateBidResultMetrics(BidderResult bidderResult, PreBidRequestContext preBidRequestContext) {
        final BidderStatus bidderStatus = bidderResult.getBidderStatus();
        final String bidder = bidderStatus.getBidder();
        final AdapterMetrics adapterMetrics = metrics.forAdapter(bidder);
        final AccountMetrics accountMetrics = metrics
                .forAccount(preBidRequestContext.getPreBidRequest().getAccountId());
        final AdapterMetrics accountAdapterMetrics = accountMetrics.forAdapter(bidder);

        for (final Bid bid : bidderResult.getBids()) {
            final long cpm = bid.getPrice().multiply(THOUSAND).longValue();
            adapterMetrics.updateHistogram(MetricName.prices, cpm);
            accountMetrics.updateHistogram(MetricName.prices, cpm);
            accountAdapterMetrics.updateHistogram(MetricName.prices, cpm);
        }

        final Integer numBids = bidderStatus.getNumBids();
        if (numBids != null) {
            accountMetrics.incCounter(MetricName.bids_received, numBids);
            accountAdapterMetrics.incCounter(MetricName.bids_received, numBids);
        } else if (Objects.equals(bidderStatus.getNoBid(), Boolean.TRUE)) {
            adapterMetrics.incCounter(MetricName.no_bid_requests);
            accountAdapterMetrics.incCounter(MetricName.no_bid_requests);
        }

        if (Objects.equals(bidderStatus.getNoCookie(), Boolean.TRUE)) {
            adapterMetrics.incCounter(MetricName.no_cookie_requests);
            accountAdapterMetrics.incCounter(MetricName.no_cookie_requests);
        }
    }

    private void updateErrorMetrics(BidderResult bidderResult, PreBidRequestContext preBidRequestContext) {
        final BidderStatus bidderStatus = bidderResult.getBidderStatus();
        final String bidder = bidderStatus.getBidder();
        final AdapterMetrics adapterMetrics = metrics.forAdapter(bidder);
        final AdapterMetrics accountAdapterMetrics = metrics
                .forAccount(preBidRequestContext.getPreBidRequest().getAccountId())
                .forAdapter(bidder);

        if (bidderResult.isTimedOut()) {
            adapterMetrics.incCounter(MetricName.timeout_requests);
            accountAdapterMetrics.incCounter(MetricName.timeout_requests);
        } else {
            adapterMetrics.incCounter(MetricName.error_requests);
            accountAdapterMetrics.incCounter(MetricName.error_requests);
        }
    }
}
