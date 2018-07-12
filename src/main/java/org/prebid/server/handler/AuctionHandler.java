package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.PreBidRequestContextFactory;
import org.prebid.server.auction.TargetingKeywordsCreator;
import org.prebid.server.auction.model.AdapterResponse;
import org.prebid.server.auction.model.PreBidRequestContext;
import org.prebid.server.auction.model.Tuple2;
import org.prebid.server.auction.model.Tuple3;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.HttpAdapterConnector;
import org.prebid.server.cache.CacheService;
import org.prebid.server.cache.proto.BidCacheResult;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.request.PreBidRequest;
import org.prebid.server.proto.response.Bid;
import org.prebid.server.proto.response.BidderStatus;
import org.prebid.server.proto.response.MediaType;
import org.prebid.server.proto.response.PreBidResponse;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AuctionHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(AuctionHandler.class);

    private static final MetricName REQUEST_TYPE_METRIC = MetricName.legacy;
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    private final ApplicationSettings applicationSettings;
    private final BidderCatalog bidderCatalog;
    private final PreBidRequestContextFactory preBidRequestContextFactory;
    private final CacheService cacheService;
    private final Metrics metrics;
    private final HttpAdapterConnector httpAdapterConnector;

    private final Clock clock;

    public AuctionHandler(ApplicationSettings applicationSettings, BidderCatalog bidderCatalog,
                          PreBidRequestContextFactory preBidRequestContextFactory,
                          CacheService cacheService, Metrics metrics,
                          HttpAdapterConnector httpAdapterConnector, Clock clock) {
        this.applicationSettings = Objects.requireNonNull(applicationSettings);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.preBidRequestContextFactory = Objects.requireNonNull(preBidRequestContextFactory);
        this.cacheService = Objects.requireNonNull(cacheService);
        this.metrics = Objects.requireNonNull(metrics);
        this.httpAdapterConnector = Objects.requireNonNull(httpAdapterConnector);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Auction handler will resolve all bidders in the incoming ad request, issue the request to the different
     * clients, then return an array of the responses.
     */
    @Override
    public void handle(RoutingContext context) {
        final long startTime = clock.millis();

        final boolean isSafari = HttpUtil.isSafari(context.request().headers().get(HttpHeaders.USER_AGENT));

        metrics.updateSafariRequestsMetric(isSafari);

        preBidRequestContextFactory.fromRequest(context)
                .recover(exception -> failWithInvalidRequest(
                        String.format("Error parsing request: %s", exception.getMessage()), exception))

                .map(preBidRequestContext ->
                        updateAppAndNoCookieAndImpsRequestedMetrics(preBidRequestContext, isSafari))

                .compose(preBidRequestContext -> applicationSettings.getAccountById(
                        preBidRequestContext.getPreBidRequest().getAccountId(), preBidRequestContext.getTimeout())
                        .compose(account -> Future.succeededFuture(Tuple2.of(preBidRequestContext, account)))
                        .recover(AuctionHandler::failWithUnknownAccountOrPropagateOriginal))

                .map((Tuple2<PreBidRequestContext, Account> result) ->
                        updateAccountRequestAndRequestTimeMetric(result, context, startTime))

                .compose((Tuple2<PreBidRequestContext, Account> result) ->
                        CompositeFuture.join(submitRequestsToAdapters(result.getLeft()))
                                .map(bidderResults -> Tuple3.of(result.getLeft(), result.getRight(),
                                        bidderResults.<AdapterResponse>list())))

                .map((Tuple3<PreBidRequestContext, Account, List<AdapterResponse>> result) ->
                        Tuple3.of(result.getLeft(), result.getMiddle(),
                                composePreBidResponse(result.getLeft(), result.getRight())))

                .compose((Tuple3<PreBidRequestContext, Account, PreBidResponse> result) ->
                        processCacheMarkup(result.getLeft(), result.getRight())
                                .recover(exception -> failWith(
                                        String.format("Prebid cache failed: %s", exception.getMessage()), exception))
                                .map(response -> Tuple3.of(result.getLeft(), result.getMiddle(), response)))

                .map((Tuple3<PreBidRequestContext, Account, PreBidResponse> result) ->
                        addTargetingKeywords(result.getLeft().getPreBidRequest(), result.getMiddle(),
                                result.getRight()))

                .setHandler(preBidResponseResult -> respondWith(bidResponseOrError(preBidResponseResult), context));
    }

    private PreBidRequestContext updateAppAndNoCookieAndImpsRequestedMetrics(
            PreBidRequestContext preBidRequestContext, boolean isSafari) {

        metrics.updateAppAndNoCookieAndImpsRequestedMetrics(preBidRequestContext.getPreBidRequest().getApp() != null,
                !preBidRequestContext.isNoLiveUids(), isSafari,
                preBidRequestContext.getPreBidRequest().getAdUnits().size());

        return preBidRequestContext;
    }

    private Tuple2<PreBidRequestContext, Account> updateAccountRequestAndRequestTimeMetric(
            Tuple2<PreBidRequestContext, Account> preBidRequestContextAccount, RoutingContext context,
            long startTime) {

        final String accountId = preBidRequestContextAccount.getLeft().getPreBidRequest().getAccountId();

        metrics.updateAccountRequestMetrics(accountId, REQUEST_TYPE_METRIC);

        setupRequestTimeUpdater(context, startTime);

        return preBidRequestContextAccount;
    }

    private void setupRequestTimeUpdater(RoutingContext context, long startTime) {
        // set up handler to update request time metric when response is sent back to a client
        context.response().endHandler(ignoredVoid -> metrics.updateRequestTimeMetric(clock.millis() - startTime));
    }

    private static <T> Future<T> failWithInvalidRequest(String message, Throwable exception) {
        return Future.failedFuture(new InvalidRequestException(message, exception));
    }

    private static <T> Future<T> failWithUnknownAccountOrPropagateOriginal(Throwable exception) {
        return exception instanceof PreBidException
                // transform into InvalidRequestException if account is unknown
                ? failWithInvalidRequest("Unknown account id: Unknown account", exception)
                // otherwise propagate exception as is because it is of system nature
                : Future.failedFuture(exception);
    }

    private static <T> Future<T> failWith(String message, Throwable exception) {
        return Future.failedFuture(new PreBidException(message, exception));
    }

    private List<Future> submitRequestsToAdapters(PreBidRequestContext preBidRequestContext) {
        return preBidRequestContext.getAdapterRequests().stream()
                .filter(ar -> bidderCatalog.isValidAdapterName(ar.getBidderCode()))
                .map(ar -> httpAdapterConnector.call(bidderCatalog.adapterByName(ar.getBidderCode()),
                        bidderCatalog.usersyncerByName(ar.getBidderCode()), ar, preBidRequestContext))
                .collect(Collectors.toList());
    }

    private PreBidResponse composePreBidResponse(PreBidRequestContext preBidRequestContext,
                                                 List<AdapterResponse> adapterResponses) {
        adapterResponses.stream()
                .filter(ar -> ar.getError() != null)
                .forEach(this::updateAdapterErrorMetrics);

        final List<BidderStatus> bidderStatuses = Stream.concat(
                adapterResponses.stream()
                        .map(AdapterResponse::getBidderStatus)
                        .peek(bs -> updateResponseTimeMetrics(bs, preBidRequestContext)),
                invalidBidderStatuses(preBidRequestContext))
                .collect(Collectors.toList());

        final List<Bid> bids = adapterResponses.stream()
                .filter(ar -> StringUtils.isBlank(ar.getBidderStatus().getError()))
                .peek(ar -> updateBidResultMetrics(ar, preBidRequestContext))
                .flatMap(ar -> ar.getBids().stream())
                .collect(Collectors.toList());

        return PreBidResponse.builder()
                .status(preBidRequestContext.isNoLiveUids() ? "no_cookie" : "OK")
                .tid(preBidRequestContext.getPreBidRequest().getTid())
                .bidderStatus(bidderStatuses)
                .bids(bids)
                .build();
    }

    private void updateAdapterErrorMetrics(AdapterResponse adapterResponse) {
        final MetricName errorMetric;
        switch (adapterResponse.getError().getType()) {
            case bad_input:
                errorMetric = MetricName.badinput;
                break;
            case bad_server_response:
                errorMetric = MetricName.badserverresponse;
                break;
            case timeout:
                errorMetric = MetricName.timeout;
                break;
            case generic:
            default:
                errorMetric = MetricName.unknown_error;
        }

        metrics.updateAdapterRequestErrorMetric(adapterResponse.getBidderStatus().getBidder(), errorMetric);
    }

    private void updateResponseTimeMetrics(BidderStatus bidderStatus, PreBidRequestContext preBidRequestContext) {
        metrics.updateAdapterResponseTime(bidderStatus.getBidder(),
                preBidRequestContext.getPreBidRequest().getAccountId(), bidderStatus.getResponseTimeMs());
    }

    private Stream<BidderStatus> invalidBidderStatuses(PreBidRequestContext preBidRequestContext) {
        return preBidRequestContext.getAdapterRequests().stream()
                .filter(b -> !bidderCatalog.isValidName(b.getBidderCode()))
                .map(b -> BidderStatus.builder().bidder(b.getBidderCode()).error("Unsupported bidder").build());
    }

    private void updateBidResultMetrics(AdapterResponse adapterResponse, PreBidRequestContext preBidRequestContext) {
        final BidderStatus bidderStatus = adapterResponse.getBidderStatus();
        final String bidder = bidderStatus.getBidder();

        metrics.updateAdapterRequestTypeAndNoCookieMetrics(bidder, REQUEST_TYPE_METRIC,
                Objects.equals(bidderStatus.getNoCookie(), Boolean.TRUE));

        final String accountId = preBidRequestContext.getPreBidRequest().getAccountId();

        for (final Bid bid : adapterResponse.getBids()) {
            final long cpm = bid.getPrice().multiply(THOUSAND).longValue();
            metrics.updateAdapterBidMetrics(bidder, accountId, cpm, bid.getAdm() != null,
                    ObjectUtils.firstNonNull(bid.getMediaType(), MediaType.banner).toString()); // default to banner
        }

        if (Objects.equals(bidderStatus.getNoBid(), Boolean.TRUE)) {
            metrics.updateAdapterRequestNobidMetrics(bidder, accountId);
        } else {
            metrics.updateAdapterRequestGotbidsMetrics(bidder, accountId);
        }
    }

    private Future<PreBidResponse> processCacheMarkup(PreBidRequestContext preBidRequestContext,
                                                      PreBidResponse preBidResponse) {
        final Future<PreBidResponse> result;

        final Integer cacheMarkup = preBidRequestContext.getPreBidRequest().getCacheMarkup();
        final List<Bid> bids = preBidResponse.getBids();
        if (!bids.isEmpty() && cacheMarkup != null && (cacheMarkup == 1 || cacheMarkup == 2)) {
            result = (cacheMarkup == 1
                    ? cacheService.cacheBids(bids, preBidRequestContext.getTimeout())
                    : cacheService.cacheBidsVideoOnly(bids, preBidRequestContext.getTimeout()))
                    .map(bidCacheResults -> mergeBidsWithCacheResults(preBidResponse, bidCacheResults));
        } else {
            result = Future.succeededFuture(preBidResponse);
        }

        return result;
    }

    private static PreBidResponse mergeBidsWithCacheResults(PreBidResponse preBidResponse,
                                                            List<BidCacheResult> bidCacheResults) {
        final List<Bid> bids = preBidResponse.getBids();
        for (int i = 0; i < bids.size(); i++) {
            final BidCacheResult result = bidCacheResults.get(i);
            // IMPORTANT: see javadoc in Bid class
            bids.get(i)
                    .setAdm(null)
                    .setNurl(null)
                    .setCacheId(result.getCacheId())
                    .setCacheUrl(result.getCacheUrl());
        }

        return preBidResponse;
    }

    /**
     * Sorts the bids and adds ad server targeting keywords to each bid.
     * The bids are sorted by cpm to find the highest bid.
     * The ad server targeting keywords are added to all bids, with specific keywords for the highest bid.
     */
    private static PreBidResponse addTargetingKeywords(PreBidRequest preBidRequest, Account account,
                                                       PreBidResponse preBidResponse) {
        final Integer sortBids = preBidRequest.getSortBids();
        if (sortBids != null && sortBids == 1) {
            final TargetingKeywordsCreator keywordsCreator =
                    TargetingKeywordsCreator.create(account.getPriceGranularity(), true, true, false);

            final Map<String, List<Bid>> adUnitCodeToBids = preBidResponse.getBids().stream()
                    .collect(Collectors.groupingBy(Bid::getCode));

            for (final List<Bid> bids : adUnitCodeToBids.values()) {
                bids.sort(Comparator.comparing(Bid::getPrice)
                        .reversed()
                        .thenComparing(Bid::getResponseTimeMs));

                for (final Bid bid : bids) {
                    // IMPORTANT: see javadoc in Bid class
                    bid.setAdServerTargeting(joinMaps(
                            keywordsCreator.makeFor(bid, bid == bids.get(0)),
                            bid.getAdServerTargeting()));
                }
            }
        }

        return preBidResponse;
    }

    private static <K, V> Map<K, V> joinMaps(Map<K, V> left, Map<K, V> right) {
        if (right != null) {
            left.putAll(right);
        }
        return left;
    }

    private static void respondWith(PreBidResponse response, RoutingContext context) {
        context.response()
                .putHeader(HttpHeaders.DATE, date())
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                .end(Json.encode(response));
    }

    private static String date() {
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now());
    }

    private PreBidResponse bidResponseOrError(AsyncResult<PreBidResponse> responseResult) {
        final MetricName responseStatus;

        final PreBidResponse result;

        if (responseResult.succeeded()) {
            responseStatus = MetricName.ok;
            result = responseResult.result();
        } else {
            final Throwable exception = responseResult.cause();
            final boolean isRequestInvalid = exception instanceof InvalidRequestException;

            responseStatus = isRequestInvalid ? MetricName.badinput : MetricName.err;

            if (!isRequestInvalid) {
                logger.error("Failed to process /auction request", exception);
            }

            result = error(isRequestInvalid || exception instanceof PreBidException
                    ? exception.getMessage()
                    : "Unexpected server error");
        }

        updateRequestMetric(responseStatus);

        return result;
    }

    private void updateRequestMetric(MetricName requestStatus) {
        metrics.updateRequestTypeMetric(REQUEST_TYPE_METRIC, requestStatus);
    }

    private static PreBidResponse error(String status) {
        return PreBidResponse.builder().status(status).build();
    }
}
