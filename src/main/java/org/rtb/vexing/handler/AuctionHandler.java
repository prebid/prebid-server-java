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
import org.rtb.vexing.auction.PreBidRequestContextFactory;
import org.rtb.vexing.auction.TargetingKeywords;
import org.rtb.vexing.cache.CacheService;
import org.rtb.vexing.cache.model.BidCacheResult;
import org.rtb.vexing.exception.PreBidException;
import org.rtb.vexing.metric.AccountMetrics;
import org.rtb.vexing.metric.AdapterMetrics;
import org.rtb.vexing.metric.MetricName;
import org.rtb.vexing.metric.Metrics;
import org.rtb.vexing.model.AdUnitBid;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
                    return applicationSettings.getAccountById(preBidRequestContext.preBidRequest.accountId)
                            .compose(account -> Future.succeededFuture(Tuple2.of(preBidRequestContext, account)))
                            .recover(exception -> failWith("Unknown account id: Unknown account", exception));
                })
                .compose(result -> {
                    final PreBidRequestContext preBidRequestContext = result.left;
                    final String accountId = preBidRequestContext.preBidRequest.accountId;

                    metrics.forAccount(accountId).incCounter(MetricName.requests);
                    setupRequestTimeUpdater(context);

                    return CompositeFuture.join(submitRequestsToAdapters(preBidRequestContext, accountId))
                            .map(bidderResults -> Tuple3.of(preBidRequestContext, result.right,
                                    bidderResults.result().<BidderResult>list()));
                })
                .map(result -> Tuple3.of(result.left, result.middle, composePreBidResponse(result.left, result.right)))
                .compose(result -> processCacheMarkup(result.left.preBidRequest, result.right)
                        .recover(exception ->
                                failWith(String.format("Prebid cache failed: %s", exception.getMessage()), exception))
                        .map(response -> Tuple3.of(result.left, result.middle, response)))
                .map(result -> addTargetingKeywords(result.left.preBidRequest, result.middle, result.right))
                .setHandler(preBidResponseResult -> respondWith(bidResponseOrError(preBidResponseResult), context));
    }

    private static <T> Future<T> failWith(String message, Throwable exception) {
        return Future.failedFuture(new PreBidException(message, exception));
    }

    private List<Future> submitRequestsToAdapters(PreBidRequestContext preBidRequestContext, String accountId) {
        return preBidRequestContext.bidders.stream()
                .filter(bidder -> adapters.isValidCode(bidder.bidderCode))
                .peek(bidder -> updateAdapterRequestMetrics(bidder.bidderCode, accountId))
                .map(bidder -> adapters.getByCode(bidder.bidderCode).requestBids(bidder, preBidRequestContext)
                        .map(bidderResult -> dropBidsWithNotValidSize(bidderResult, bidder.adUnitBids)))
                .collect(Collectors.toList());
    }

    /**
     * Removes from Bidder result bids with zero width or height if it is not possible to find these values
     * in correspond AdUnitBid
     */
    private static BidderResult dropBidsWithNotValidSize(BidderResult bidderResult, List<AdUnitBid> adUnitBids) {
        final List<Bid> notValidBids = bidderResult.bids.stream()
                .filter(bid -> Objects.equals("banner", bid.mediaType) && (bid.height == null || bid.height == 0
                        || bid.width == null || bid.width == 0))
                .collect(Collectors.toList());

        // bids which are not in invalid list are valid
        final List<Bid> validBids = new ArrayList<>(bidderResult.bids);
        validBids.removeAll(notValidBids);

        for (final Bid bid : notValidBids) {
            Optional<AdUnitBid> matchingAdUnit = adUnitBids.stream()
                    .filter(adUnitBid -> adUnitBid.adUnitCode.equals(bid.code) && adUnitBid.bidId.equals(bid.bidId)
                            && adUnitBid.sizes.size() == 1)
                    .findAny();
            if (matchingAdUnit.isPresent()) {
                final Bid validBid = bid.toBuilder()
                        .width(matchingAdUnit.get().sizes.get(0).getW())
                        .height(matchingAdUnit.get().sizes.get(0).getH())
                        .build();
                validBids.add(validBid);
            } else {
                logger.warn("Bid was rejected for bidder {0} because no size was defined", bid.bidder);
            }
        }
        if (bidderResult.bids.size() != validBids.size()) {
            bidderResult = bidderResult.toBuilder()
                    .bids(validBids)
                    .bidderStatus(bidderResult.bidderStatus.toBuilder().numBids(validBids.size()).build())
                    .build();
        }
        return bidderResult;
    }

    private PreBidResponse composePreBidResponse(PreBidRequestContext preBidRequestContext,
                                                 List<BidderResult> bidderResults) {
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

        return PreBidResponse.builder()
                .status(preBidRequestContext.noLiveUids ? "no_cookie" : "OK")
                .tid(preBidRequestContext.preBidRequest.tid)
                .bidderStatus(bidderStatuses)
                .bids(bids)
                .build();
    }

    private Future<PreBidResponse> processCacheMarkup(PreBidRequest preBidRequest, PreBidResponse preBidResponse) {
        final Future<PreBidResponse> result;

        final List<Bid> bids = preBidResponse.bids;
        if (preBidRequest.cacheMarkup != null && preBidRequest.cacheMarkup == 1 && !bids.isEmpty()) {
            result = cacheService.saveBids(bids)
                    .map(bidCacheResults -> mergeBidsWithCacheResults(preBidResponse, bidCacheResults));
        } else {
            result = Future.succeededFuture(preBidResponse);
        }

        return result;
    }

    private PreBidResponse mergeBidsWithCacheResults(PreBidResponse preBidResponse,
                                                     List<BidCacheResult> bidCacheResults) {
        final List<Bid> bids = preBidResponse.bids;
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

        if (preBidRequest.sortBids != null && preBidRequest.sortBids == 1) {
            final List<Bid> bidsWithKeywords = preBidResponse.bids.stream()
                    .collect(Collectors.groupingBy(bid -> bid.code))
                    .values().stream()
                    .peek(bids -> bids.sort(Comparator.<Bid, BigDecimal>comparing(bid -> bid.price)
                            .reversed()
                            .thenComparing(bid -> bid.responseTimeMs)))
                    .flatMap(bids ->
                            TargetingKeywords.addTargetingKeywords(preBidRequest, bids, account).stream())
                    .collect(Collectors.toList());

            result = preBidResponse.toBuilder().bids(bidsWithKeywords).build();
        }

        return result;
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
        return preBidRequestContext.bidders.stream()
                .filter(b -> !adapters.isValidCode(b.bidderCode))
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
        } else if (preBidRequestContext.noLiveUids) {
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
        metrics.forAdapter(bidderStatus.bidder).updateTimer(MetricName.request_time, bidderStatus.responseTimeMs);
        metrics.forAccount(preBidRequestContext.preBidRequest.accountId).forAdapter(bidderStatus.bidder)
                .updateTimer(MetricName.request_time, bidderStatus.responseTimeMs);
    }

    private void updateBidResultMetrics(BidderResult bidderResult, PreBidRequestContext preBidRequestContext) {
        final BidderStatus bidderStatus = bidderResult.bidderStatus;
        final AdapterMetrics adapterMetrics = metrics.forAdapter(bidderStatus.bidder);
        final AccountMetrics accountMetrics = metrics.forAccount(preBidRequestContext.preBidRequest.accountId);
        final AdapterMetrics accountAdapterMetrics = accountMetrics.forAdapter(bidderStatus.bidder);

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
                .forAdapter(bidderResult.bidderStatus.bidder);
        final AdapterMetrics accountAdapterMetrics = metrics.forAccount(preBidRequestContext.preBidRequest.accountId)
                .forAdapter(bidderResult.bidderStatus.bidder);

        if (bidderResult.timedOut) {
            adapterMetrics.incCounter(MetricName.timeout_requests);
            accountAdapterMetrics.incCounter(MetricName.timeout_requests);
        } else {
            adapterMetrics.incCounter(MetricName.error_requests);
            accountAdapterMetrics.incCounter(MetricName.error_requests);
        }
    }
}
