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
import org.prebid.server.gdpr.GdprService;
import org.prebid.server.gdpr.GdprUtils;
import org.prebid.server.gdpr.model.GdprPurpose;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AuctionHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(AuctionHandler.class);

    private static final MetricName REQUEST_TYPE_METRIC = MetricName.legacy;
    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);
    private static final Set<GdprPurpose> GDPR_PURPOSES =
            Collections.unmodifiableSet(EnumSet.of(GdprPurpose.informationStorageAndAccess));

    private final ApplicationSettings applicationSettings;
    private final BidderCatalog bidderCatalog;
    private final PreBidRequestContextFactory preBidRequestContextFactory;
    private final CacheService cacheService;
    private final Metrics metrics;
    private final HttpAdapterConnector httpAdapterConnector;
    private final Clock clock;
    private final GdprService gdprService;
    private final Integer gdprHostVendorId;
    private final boolean useGeoLocation;

    public AuctionHandler(ApplicationSettings applicationSettings, BidderCatalog bidderCatalog,
                          PreBidRequestContextFactory preBidRequestContextFactory,
                          CacheService cacheService, Metrics metrics,
                          HttpAdapterConnector httpAdapterConnector, Clock clock,
                          GdprService gdprService, Integer gdprHostVendorId, boolean useGeoLocation) {
        this.applicationSettings = Objects.requireNonNull(applicationSettings);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.preBidRequestContextFactory = Objects.requireNonNull(preBidRequestContextFactory);
        this.cacheService = Objects.requireNonNull(cacheService);
        this.metrics = Objects.requireNonNull(metrics);
        this.httpAdapterConnector = Objects.requireNonNull(httpAdapterConnector);
        this.clock = Objects.requireNonNull(clock);
        this.gdprService = Objects.requireNonNull(gdprService);
        this.gdprHostVendorId = gdprHostVendorId;
        this.useGeoLocation = useGeoLocation;
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

                .map(this::updateAccountRequestAndRequestTimeMetric)

                .compose((Tuple2<PreBidRequestContext, Account> result) ->
                        CompositeFuture.join(submitRequestsToAdapters(result.getLeft()))
                                .map(bidderResults -> Tuple3.of(result.getLeft(), result.getRight(),
                                        bidderResults.<AdapterResponse>list())))

                .compose((Tuple3<PreBidRequestContext, Account, List<AdapterResponse>> result) ->
                        resolveVendorsToGdpr(result.getLeft(), result.getRight())
                                .map(vendorsToGdpr -> Tuple3.of(result.getLeft(), result.getMiddle(),
                                        composePreBidResponse(result.getLeft(), result.getRight(), vendorsToGdpr))))

                .compose((Tuple3<PreBidRequestContext, Account, PreBidResponse> result) ->
                        processCacheMarkup(result.getLeft(), result.getRight())
                                .recover(exception -> failWith(
                                        String.format("Prebid cache failed: %s", exception.getMessage()), exception))
                                .map(response -> Tuple3.of(result.getLeft(), result.getMiddle(), response)))

                .map((Tuple3<PreBidRequestContext, Account, PreBidResponse> result) ->
                        addTargetingKeywords(result.getLeft().getPreBidRequest(), result.getMiddle(),
                                result.getRight()))

                .setHandler(preBidResponseResult ->
                        respondWith(bidResponseOrError(preBidResponseResult), context, startTime));
    }

    private PreBidRequestContext updateAppAndNoCookieAndImpsRequestedMetrics(
            PreBidRequestContext preBidRequestContext, boolean isSafari) {

        metrics.updateAppAndNoCookieAndImpsRequestedMetrics(preBidRequestContext.getPreBidRequest().getApp() != null,
                !preBidRequestContext.isNoLiveUids(), isSafari,
                preBidRequestContext.getPreBidRequest().getAdUnits().size());

        return preBidRequestContext;
    }

    private Tuple2<PreBidRequestContext, Account> updateAccountRequestAndRequestTimeMetric(
            Tuple2<PreBidRequestContext, Account> preBidRequestContextAccount) {

        final String accountId = preBidRequestContextAccount.getLeft().getPreBidRequest().getAccountId();
        metrics.updateAccountRequestMetrics(accountId, REQUEST_TYPE_METRIC);

        return preBidRequestContextAccount;
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

    private Future<Map<Integer, Boolean>> resolveVendorsToGdpr(PreBidRequestContext preBidRequestContext,
                                                               List<AdapterResponse> adapterResponses) {
        final Set<Integer> vendorIds = adapterResponses.stream()
                .map(adapterResponse -> adapterResponse.getBidderStatus().getBidder())
                .filter(bidderCatalog::isActive)
                .map(bidder -> bidderCatalog.metaInfoByName(bidder).info().getGdpr().getVendorId())
                .collect(Collectors.toSet());

        final boolean hostVendorIdIsMissing = gdprHostVendorId != null && !vendorIds.contains(gdprHostVendorId);
        if (hostVendorIdIsMissing) {
            vendorIds.add(gdprHostVendorId);
        }

        final String gdpr = GdprUtils.gdprFrom(preBidRequestContext.getPreBidRequest().getRegs());
        final String gdprConsent = GdprUtils.gdprConsentFrom(preBidRequestContext.getPreBidRequest().getUser());
        final String ip = useGeoLocation ? preBidRequestContext.getIp() : null;

        return gdprService.resultByVendor(GDPR_PURPOSES, vendorIds, gdpr, gdprConsent, ip,
                preBidRequestContext.getTimeout())
                .map(gdprResponse -> toVendorsToGdpr(gdprResponse.getVendorsToGdpr(), hostVendorIdIsMissing));
    }

    private Map<Integer, Boolean> toVendorsToGdpr(Map<Integer, Boolean> vendorsToGdpr, boolean hostVendorIdIsMissing) {
        final Map<Integer, Boolean> result;

        if (Objects.equals(vendorsToGdpr.get(gdprHostVendorId), false)) {
            result = Collections.emptyMap(); // deny all by host vendor
        } else if (hostVendorIdIsMissing) {
            final Map<Integer, Boolean> vendorsToGdprWithoutHost = new HashMap<>(vendorsToGdpr);
            vendorsToGdprWithoutHost.remove(gdprHostVendorId); // just to be clean with bidders
            result = vendorsToGdprWithoutHost;
        } else {
            result = vendorsToGdpr;
        }

        return result;
    }

    private PreBidResponse composePreBidResponse(PreBidRequestContext preBidRequestContext,
                                                 List<AdapterResponse> adapterResponses,
                                                 Map<Integer, Boolean> vendorsToGdpr) {
        adapterResponses.stream()
                .filter(ar -> ar.getError() != null)
                .forEach(this::updateAdapterErrorMetrics);

        final List<BidderStatus> bidderStatuses = Stream.concat(
                adapterResponses.stream()
                        .map(adapterResponse -> updateBidderStatus(adapterResponse.getBidderStatus(), vendorsToGdpr))
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

    private BidderStatus updateBidderStatus(BidderStatus bidderStatus, Map<Integer, Boolean> vendorsToGdpr) {
        final int vendorId = bidderCatalog.metaInfoByName(bidderStatus.getBidder()).info().getGdpr().getVendorId();
        return Objects.equals(vendorsToGdpr.get(vendorId), true)
                ? bidderStatus
                : bidderStatus.toBuilder().usersync(null).build();
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

    private void respondWith(PreBidResponse response, RoutingContext context, long startTime) {
        // don't send the response if client has gone
        if (context.response().closed()) {
            logger.warn("The client already closed connection, response will be skipped.");
            return;
        }

        context.response().exceptionHandler(this::handleResponseException);

        context.response()
                .putHeader(HttpHeaders.DATE, date())
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                .end(Json.encode(response));

        metrics.updateRequestTimeMetric(clock.millis() - startTime);
    }

    private void handleResponseException(Throwable throwable) {
        logger.warn("Failed to send auction response", throwable);
        metrics.updateRequestTypeMetric(REQUEST_TYPE_METRIC, MetricName.networkerr);
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
