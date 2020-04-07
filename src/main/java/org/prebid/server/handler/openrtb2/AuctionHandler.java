package org.prebid.server.handler.openrtb2;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.analytics.model.HttpContext;
import org.prebid.server.auction.AuctionRequestFactory;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.Tuple2;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.exception.BlacklistedAccountException;
import org.prebid.server.exception.BlacklistedAppException;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.UnauthorizedAccountException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.manager.AdminManager;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.util.HttpUtil;

import java.time.Clock;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class AuctionHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(AuctionHandler.class);
    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(logger);

    private final AuctionRequestFactory auctionRequestFactory;
    private final ExchangeService exchangeService;
    private final AnalyticsReporter analyticsReporter;
    private final Metrics metrics;
    private final Clock clock;
    private final AdminManager adminManager;
    private final JacksonMapper mapper;

    public AuctionHandler(AuctionRequestFactory auctionRequestFactory,
                          ExchangeService exchangeService,
                          AnalyticsReporter analyticsReporter,
                          Metrics metrics,
                          Clock clock,
                          AdminManager adminManager,
                          JacksonMapper mapper) {

        this.auctionRequestFactory = Objects.requireNonNull(auctionRequestFactory);
        this.exchangeService = Objects.requireNonNull(exchangeService);
        this.analyticsReporter = Objects.requireNonNull(analyticsReporter);
        this.metrics = Objects.requireNonNull(metrics);
        this.clock = Objects.requireNonNull(clock);
        this.adminManager = Objects.requireNonNull(adminManager);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        // Prebid Server interprets request.tmax to be the maximum amount of time that a caller is willing to wait
        // for bids. However, tmax may be defined in the Stored Request data.
        // If so, then the trip to the backend might use a significant amount of this time. We can respect timeouts
        // more accurately if we note the real start time, and use it to compute the auction timeout.
        final long startTime = clock.millis();

        final boolean isSafari = HttpUtil.isSafari(routingContext.request().headers().get(HttpUtil.USER_AGENT_HEADER));
        metrics.updateSafariRequestsMetric(isSafari);

        final AuctionEvent.AuctionEventBuilder auctionEventBuilder = AuctionEvent.builder()
                .httpContext(HttpContext.from(routingContext));

        auctionRequestFactory.fromRequest(routingContext, startTime)
                .map(context -> context.toBuilder()
                        .requestTypeMetric(requestTypeMetric(context.getBidRequest()))
                        .build())

                .map(context -> addToEvent(context, auctionEventBuilder::auctionContext, context))
                .map(context -> updateAppAndNoCookieAndImpsMetrics(context, isSafari))

                .compose(context -> exchangeService.holdAuction(context)
                        .map(bidResponse -> Tuple2.of(bidResponse, context)))

                .map(result -> addToEvent(result.getLeft(), auctionEventBuilder::bidResponse, result))
                .setHandler(result -> handleResult(result, auctionEventBuilder, routingContext, startTime));
    }

    private static MetricName requestTypeMetric(BidRequest bidRequest) {
        return bidRequest.getApp() != null ? MetricName.openrtb2app : MetricName.openrtb2web;
    }

    private static <T, R> R addToEvent(T field, Consumer<T> consumer, R result) {
        consumer.accept(field);
        return result;
    }

    private AuctionContext updateAppAndNoCookieAndImpsMetrics(AuctionContext context, boolean isSafari) {
        final BidRequest bidRequest = context.getBidRequest();
        final UidsCookie uidsCookie = context.getUidsCookie();

        final List<Imp> imps = bidRequest.getImp();
        metrics.updateAppAndNoCookieAndImpsRequestedMetrics(bidRequest.getApp() != null, uidsCookie.hasLiveUids(),
                isSafari, imps.size());

        metrics.updateImpTypesMetrics(imps);

        return context;
    }

    private void handleResult(AsyncResult<Tuple2<BidResponse, AuctionContext>> responseResult,
                              AuctionEvent.AuctionEventBuilder auctionEventBuilder, RoutingContext context,
                              long startTime) {
        final boolean responseSucceeded = responseResult.succeeded();

        final MetricName requestType = responseSucceeded
                ? responseResult.result().getRight().getRequestTypeMetric()
                : MetricName.openrtb2web;

        final MetricName metricRequestStatus;
        final List<String> errorMessages;
        final int status;
        final String body;

        if (responseSucceeded) {
            metricRequestStatus = MetricName.ok;
            errorMessages = Collections.emptyList();

            status = HttpResponseStatus.OK.code();
            context.response().headers().add(HttpUtil.CONTENT_TYPE_HEADER, HttpHeaderValues.APPLICATION_JSON);
            body = mapper.encode(responseResult.result().getLeft());
        } else {
            final Throwable exception = responseResult.cause();
            if (exception instanceof InvalidRequestException) {
                metricRequestStatus = MetricName.badinput;

                final InvalidRequestException invalidRequestException = (InvalidRequestException) exception;
                errorMessages = invalidRequestException.getMessages().stream()
                        .map(msg -> String.format("Invalid request format: %s", msg))
                        .collect(Collectors.toList());
                final String message = String.join("\n", errorMessages);
                adminManager.accept(AdminManager.COUNTER_KEY, logger,
                        logMessageFrom(invalidRequestException, message, context));

                status = HttpResponseStatus.BAD_REQUEST.code();
                body = message;
            } else if (exception instanceof UnauthorizedAccountException) {
                metricRequestStatus = MetricName.badinput;
                final String message = String.format("Unauthorized: %s", exception.getMessage());
                conditionalLogger.info(message, 100);
                errorMessages = Collections.singletonList(message);

                status = HttpResponseStatus.UNAUTHORIZED.code();
                body = message;
                final String accountId = ((UnauthorizedAccountException) exception).getAccountId();
                metrics.updateAccountRequestRejectedMetrics(accountId);
            } else if (exception instanceof BlacklistedAppException
                    || exception instanceof BlacklistedAccountException) {
                metricRequestStatus = exception instanceof BlacklistedAccountException
                        ? MetricName.blacklisted_account : MetricName.blacklisted_app;
                final String message = String.format("Blacklisted: %s", exception.getMessage());
                logger.debug(message);

                errorMessages = Collections.singletonList(message);
                status = HttpResponseStatus.FORBIDDEN.code();
                body = message;
            } else {
                metricRequestStatus = MetricName.err;
                logger.error("Critical error while running the auction", exception);

                final String message = exception.getMessage();
                errorMessages = Collections.singletonList(message);

                status = HttpResponseStatus.INTERNAL_SERVER_ERROR.code();
                body = String.format("Critical error while running the auction: %s", message);
            }
        }

        final AuctionEvent auctionEvent = auctionEventBuilder.status(status).errors(errorMessages).build();
        respondWith(context, status, body, startTime, requestType, metricRequestStatus, auctionEvent);
    }

    private static String logMessageFrom(InvalidRequestException exception, String message, RoutingContext context) {
        return exception.isNeedEnhancedLogging()
                ? String.format("%s, Referer: %s", message, context.request().headers().get(HttpUtil.REFERER_HEADER))
                : message;
    }

    private void respondWith(RoutingContext context, int status, String body, long startTime, MetricName requestType,
                             MetricName metricRequestStatus, AuctionEvent event) {
        // don't send the response if client has gone
        if (context.response().closed()) {
            logger.warn("The client already closed connection, response will be skipped");
            metrics.updateRequestTypeMetric(requestType, MetricName.networkerr);
        } else {
            context.response()
                    .exceptionHandler(throwable -> handleResponseException(throwable, requestType))
                    .setStatusCode(status)
                    .end(body);

            metrics.updateRequestTimeMetric(clock.millis() - startTime);
            metrics.updateRequestTypeMetric(requestType, metricRequestStatus);
            analyticsReporter.processEvent(event);
        }
    }

    private void handleResponseException(Throwable throwable, MetricName requestType) {
        logger.warn("Failed to send auction response: {0}", throwable.getMessage());
        metrics.updateRequestTypeMetric(requestType, MetricName.networkerr);
    }
}
