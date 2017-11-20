package org.rtb.vexing.handler;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.rtb.vexing.adapter.Adapter;
import org.rtb.vexing.adapter.AdapterCatalog;
import org.rtb.vexing.cache.CacheService;
import org.rtb.vexing.cache.model.BidCacheResult;
import org.rtb.vexing.cookie.UidsCookie;
import org.rtb.vexing.metric.MetricName;
import org.rtb.vexing.metric.Metrics;
import org.rtb.vexing.model.AdUnitBid;
import org.rtb.vexing.model.Bidder;
import org.rtb.vexing.model.BidderResult;
import org.rtb.vexing.model.request.PreBidRequest;
import org.rtb.vexing.model.response.Bid;
import org.rtb.vexing.model.response.BidderStatus;
import org.rtb.vexing.model.response.PreBidResponse;
import org.rtb.vexing.settings.ApplicationSettings;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class AuctionHandler {

    private static final Logger logger = LoggerFactory.getLogger(AuctionHandler.class);

    private final ApplicationSettings applicationSettings;
    private final AdapterCatalog adapters;
    private final CacheService cacheService;
    private final Metrics metrics;

    private String date;
    private final Clock clock = Clock.systemDefaultZone();

    public AuctionHandler(ApplicationSettings applicationSettings, AdapterCatalog adapters, CacheService cacheService,
                          Vertx vertx, Metrics metrics) {
        this.applicationSettings = Objects.requireNonNull(applicationSettings);
        this.adapters = Objects.requireNonNull(adapters);
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

        final JsonObject json = context.getBodyAsJson();
        if (json == null) {
            logger.error("Incoming request has no body.");
            context.response().setStatusCode(400).end();
            return;
        }

        final PreBidRequest preBidRequest = json.mapTo(PreBidRequest.class);
        // FIXME: metrics.incCounter(MetricName.error_requests) if request couldn't be parsed
        final UidsCookie uidsCookie = UidsCookie.parseFromRequest(context);

        final long requestStarted = clock.millis();

        if (preBidRequest.app != null) {
            metrics.incCounter(MetricName.app_requests);
        } else if (!uidsCookie.hasLiveUids()) {
            metrics.incCounter(MetricName.no_cookie_requests);
            if (isSafari) {
                metrics.incCounter(MetricName.safari_no_cookie_requests);
            }
            // FIXME: set no_cookie status
        }

        if (!applicationSettings.getAccountById(preBidRequest.accountId).isPresent()) {
            respondWith(error("Unknown account id"), context);
            metrics.incCounter(MetricName.error_requests);
            return;
        }

        metrics.forAccount(preBidRequest.accountId).incCounter(MetricName.requests);

        context.response().endHandler(ignored -> metrics.updateTimer(MetricName.request_time,
                clock.millis() - requestStarted));

        final List<Bidder> bidders = extractBidders(preBidRequest);
        final List<Future> bidderResponseFutures = bidders.stream()
                .map(bidder -> adapters.get(bidder.bidderCode)
                        .requestBids(bidder, preBidRequest, uidsCookie, context.request()))
                .collect(Collectors.toList());

        // FIXME: are we tolerating individual exchange failures?
        CompositeFuture.join(bidderResponseFutures)
                .compose(bidderResponsesResult -> composePreBidResponse(preBidRequest, bidderResponsesResult))
                .compose(preBidResponse -> processCacheMarkup(preBidRequest, preBidResponse))
                .setHandler(preBidResponseResult -> respondWith(bidResponseOrNoBid(preBidResponseResult), context));
    }

    private Future<PreBidResponse> composePreBidResponse(PreBidRequest preBidRequest,
                                                         CompositeFuture bidderResponsesResult) {
        final List<BidderResult> bidderResults = bidderResponsesResult.result().list();

        final List<BidderStatus> bidderStatuses = bidderResults.stream()
                .map(br -> br.bidderStatus)
                .collect(Collectors.toList());

        final List<Bid> bids = bidderResults.stream()
                .flatMap(br -> br.bids.stream())
                .collect(Collectors.toList());

        final PreBidResponse response = createPreBidResponse(preBidRequest, bidderStatuses, bids);
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

                        final PreBidResponse response =
                                createPreBidResponse(preBidRequest, preBidResponse.bidderStatus, bidsWithCacheUUIDs);
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

    private static PreBidResponse bidResponseOrNoBid(AsyncResult<PreBidResponse> responseResult) {
        return responseResult.succeeded() ? responseResult.result() : Adapter.NO_BID_RESPONSE;
    }

    private static PreBidResponse createPreBidResponse(PreBidRequest preBidRequest, List<BidderStatus> bidderStatuses,
                                                       List<Bid> bids) {
        return PreBidResponse.builder()
                .status("OK") // FIXME: might be "no_cookie"
                .tid(preBidRequest.tid)
                .bidderStatus(bidderStatuses)
                .bids(bids)
                .build();
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

    private static List<Bidder> extractBidders(PreBidRequest preBidRequest) {
        return preBidRequest.adUnits.stream()
                .flatMap(unit -> unit.bids.stream().map(bid -> AdUnitBid.from(unit, bid)))
                .collect(Collectors.groupingBy(a -> a.bidderCode))
                .entrySet().stream()
                .map(e -> Bidder.from(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    private static PreBidResponse error(String status) {
        return PreBidResponse.builder().status(status).build();
    }
}
