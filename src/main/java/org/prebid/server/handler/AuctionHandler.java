package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
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
import org.prebid.server.exception.PreBidException;
import org.prebid.server.metric.prebid.AuctionHandlerMetrics;
import org.prebid.server.proto.request.PreBidRequest;
import org.prebid.server.proto.response.Bid;
import org.prebid.server.proto.response.BidderStatus;
import org.prebid.server.proto.response.PreBidResponse;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AuctionHandler extends AbstractMeteredHandler<AuctionHandlerMetrics> {

    private static final Logger logger = LoggerFactory.getLogger(AuctionHandler.class);

    private final ApplicationSettings applicationSettings;
    private final BidderCatalog bidderCatalog;
    private final PreBidRequestContextFactory preBidRequestContextFactory;
    private final CacheService cacheService;
    private final HttpAdapterConnector httpAdapterConnector;

    public AuctionHandler(ApplicationSettings applicationSettings, BidderCatalog bidderCatalog,
                          PreBidRequestContextFactory preBidRequestContextFactory,
                          CacheService cacheService,
                          AuctionHandlerMetrics handlerMetrics,
                          HttpAdapterConnector httpAdapterConnector, Clock clock) {
        super(handlerMetrics, clock);
        this.applicationSettings = Objects.requireNonNull(applicationSettings);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.preBidRequestContextFactory = Objects.requireNonNull(preBidRequestContextFactory);
        this.cacheService = Objects.requireNonNull(cacheService);
        this.httpAdapterConnector = Objects.requireNonNull(httpAdapterConnector);
    }

    /**
     * Auction handler will resolve all bidders in the incoming ad request, issue the request to the different
     * clients, then return an array of the responses.
     */
    @Override
    public void handle(RoutingContext context) {
        getHandlerMetrics().updateRequestMetrics(context, this);

        preBidRequestContextFactory.fromRequest(context)
                .recover(exception ->
                        failWith(String.format("Error parsing request: %s", exception.getMessage()), exception))
                .map(preBidRequestContext -> getHandlerMetrics().updateAppAndNoCookieMetrics(context, this,
                        preBidRequestContext, preBidRequestContext.isNoLiveUids(),
                        preBidRequestContext.getPreBidRequest().getApp() != null))

                .compose(preBidRequestContext -> applicationSettings.getAccountById(
                        preBidRequestContext.getPreBidRequest().getAccountId(), preBidRequestContext.getTimeout())
                        .compose(account -> Future.succeededFuture(Tuple2.of(preBidRequestContext, account)))
                        .recover(exception -> failWith("Unknown account id: Unknown account", exception)))

                .map((Tuple2<PreBidRequestContext, Account> result) ->
                        getHandlerMetrics().updateAccountRequestAndRequestTimeMetric(result, context))

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

                .setHandler(preBidResponseResult ->
                        respondWith(getHandlerMetrics().bidResponseOrError(preBidResponseResult), context));
    }

    private static <T> Future<T> failWith(String message, Throwable exception) {
        return Future.failedFuture(new PreBidException(message, exception));
    }

    private List<Future> submitRequestsToAdapters(PreBidRequestContext preBidRequestContext) {
        final String accountId = preBidRequestContext.getPreBidRequest().getAccountId();
        return preBidRequestContext.getAdapterRequests().stream()
                .filter(ar -> bidderCatalog.isValidAdapterName(ar.getBidderCode()))
                .peek(ar -> getHandlerMetrics().updateAdapterRequestMetrics(ar.getBidderCode(), accountId))
                .map(ar -> httpAdapterConnector.call(bidderCatalog.adapterByName(ar.getBidderCode()),
                        bidderCatalog.usersyncerByName(ar.getBidderCode()), ar, preBidRequestContext))
                .collect(Collectors.toList());
    }

    private PreBidResponse composePreBidResponse(PreBidRequestContext preBidRequestContext,
                                                 List<AdapterResponse> adapterResponses) {
        adapterResponses.stream()
                .filter(ar -> StringUtils.isNotBlank(ar.getBidderStatus().getError()))
                .forEach(ar -> getHandlerMetrics().updateErrorMetrics(ar, preBidRequestContext));

        final List<BidderStatus> bidderStatuses = Stream.concat(
                adapterResponses.stream()
                        .map(AdapterResponse::getBidderStatus)
                        .peek(bs -> getHandlerMetrics().updateResponseTimeMetrics(bs, preBidRequestContext)),
                invalidBidderStatuses(preBidRequestContext))
                .collect(Collectors.toList());

        final List<Bid> bids = adapterResponses.stream()
                .filter(ar -> StringUtils.isBlank(ar.getBidderStatus().getError()))
                .peek(ar -> getHandlerMetrics().updateBidResultMetrics(ar, preBidRequestContext))
                .flatMap(ar -> ar.getBids().stream())
                .collect(Collectors.toList());

        return PreBidResponse.builder()
                .status(preBidRequestContext.isNoLiveUids() ? "no_cookie" : "OK")
                .tid(preBidRequestContext.getPreBidRequest().getTid())
                .bidderStatus(bidderStatuses)
                .bids(bids)
                .build();
    }

    private Stream<BidderStatus> invalidBidderStatuses(PreBidRequestContext preBidRequestContext) {
        return preBidRequestContext.getAdapterRequests().stream()
                .filter(b -> !bidderCatalog.isValidName(b.getBidderCode()))
                .map(b -> BidderStatus.builder().bidder(b.getBidderCode()).error("Unsupported bidder").build());
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
                    TargetingKeywordsCreator.create(account.getPriceGranularity(), true, false);

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

}
