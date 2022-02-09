package org.prebid.server.handler.openrtb2;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.analytics.AnalyticsReporterDelegator;
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.requestfactory.AuctionRequestFactory;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.exception.BlacklistedAccountException;
import org.prebid.server.exception.BlacklistedAppException;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.UnauthorizedAccountException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.log.HttpInteractionLogger;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.model.Endpoint;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.version.PrebidVersionProvider;

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
    private final AnalyticsReporterDelegator analyticsDelegator;
    private final Metrics metrics;
    private final Clock clock;
    private final HttpInteractionLogger httpInteractionLogger;
    private final PrebidVersionProvider prebidVersionProvider;
    private final JacksonMapper mapper;

    public AuctionHandler(AuctionRequestFactory auctionRequestFactory,
                          ExchangeService exchangeService,
                          AnalyticsReporterDelegator analyticsDelegator,
                          Metrics metrics,
                          Clock clock,
                          HttpInteractionLogger httpInteractionLogger,
                          PrebidVersionProvider prebidVersionProvider,
                          JacksonMapper mapper) {

        this.auctionRequestFactory = Objects.requireNonNull(auctionRequestFactory);
        this.exchangeService = Objects.requireNonNull(exchangeService);
        this.analyticsDelegator = Objects.requireNonNull(analyticsDelegator);
        this.metrics = Objects.requireNonNull(metrics);
        this.clock = Objects.requireNonNull(clock);
        this.httpInteractionLogger = Objects.requireNonNull(httpInteractionLogger);
        this.prebidVersionProvider = Objects.requireNonNull(prebidVersionProvider);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        // Prebid Server interprets request.tmax to be the maximum amount of time that a caller is willing to wait
        // for bids. However, tmax may be defined in the Stored Request data.
        // If so, then the trip to the backend might use a significant amount of this time. We can respect timeouts
        // more accurately if we note the real start time, and use it to compute the auction timeout.
        final long startTime = clock.millis();

        final AuctionEvent.AuctionEventBuilder auctionEventBuilder = AuctionEvent.builder()
                .httpContext(HttpRequestContext.from(routingContext));

        auctionRequestFactory.fromRequest(routingContext, startTime)

                .map(this::updateAppAndNoCookieAndImpsMetrics)

                // In case of holdAuction Exception and auctionContext is not present below
                .map(context -> addToEvent(context, auctionEventBuilder::auctionContext, context))

                .compose(exchangeService::holdAuction)
                // populate event with updated context
                .map(context -> addToEvent(context, auctionEventBuilder::auctionContext, context))
                .map(context -> addToEvent(context.getBidResponse(), auctionEventBuilder::bidResponse, context))
                .onComplete(context -> handleResult(context, auctionEventBuilder, routingContext, startTime));
    }

    private static <T, R> R addToEvent(T field, Consumer<T> consumer, R result) {
        consumer.accept(field);
        return result;
    }

    private AuctionContext updateAppAndNoCookieAndImpsMetrics(AuctionContext context) {
        if (!context.isRequestRejected()) {
            final BidRequest bidRequest = context.getBidRequest();
            final UidsCookie uidsCookie = context.getUidsCookie();

            final List<Imp> imps = bidRequest.getImp();
            metrics.updateAppAndNoCookieAndImpsRequestedMetrics(
                    bidRequest.getApp() != null,
                    uidsCookie.hasLiveUids(),
                    imps.size());

            metrics.updateImpTypesMetrics(imps);
        }

        return context;
    }

    private void handleResult(AsyncResult<AuctionContext> responseResult,
                              AuctionEvent.AuctionEventBuilder auctionEventBuilder,
                              RoutingContext routingContext,
                              long startTime) {
        final boolean responseSucceeded = responseResult.succeeded();

        final AuctionContext auctionContext = responseSucceeded ? responseResult.result() : null;
        final MetricName requestType = responseSucceeded
                ? auctionContext.getRequestTypeMetric()
                : MetricName.openrtb2web;

        final MetricName metricRequestStatus;
        final List<String> errorMessages;
        final HttpResponseStatus status;
        final String body;

        final HttpServerResponse response = routingContext.response();
        enrichWithCommonHeaders(response);

        if (responseSucceeded) {
            metricRequestStatus = MetricName.ok;
            errorMessages = Collections.emptyList();

            status = HttpResponseStatus.OK;
            enrichWithSuccessfulHeaders(response);
            body = mapper.encodeToString(responseResult.result().getBidResponse());
        } else {
            final Throwable exception = responseResult.cause();
            if (exception instanceof InvalidRequestException) {
                metricRequestStatus = MetricName.badinput;

                final InvalidRequestException invalidRequestException = (InvalidRequestException) exception;
                errorMessages = invalidRequestException.getMessages().stream()
                        .map(msg -> String.format("Invalid request format: %s", msg))
                        .collect(Collectors.toList());
                final String message = String.join("\n", errorMessages);
                final String referer = routingContext.request().headers().get(HttpUtil.REFERER_HEADER);
                conditionalLogger.info(String.format("%s, Referer: %s", message, referer), 0.01);

                status = HttpResponseStatus.BAD_REQUEST;
                body = message;
            } else if (exception instanceof UnauthorizedAccountException) {
                metricRequestStatus = MetricName.badinput;
                final String message = exception.getMessage();
                conditionalLogger.info(message, 0.01);
                errorMessages = Collections.singletonList(message);

                status = HttpResponseStatus.UNAUTHORIZED;

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
                status = HttpResponseStatus.FORBIDDEN;
                body = message;
            } else {
                metricRequestStatus = MetricName.err;
                logger.error("Critical error while running the auction", exception);

                final String message = exception.getMessage();
                errorMessages = Collections.singletonList(message);

                status = HttpResponseStatus.INTERNAL_SERVER_ERROR;
                body = String.format("Critical error while running the auction: %s", message);
            }
        }

        final AuctionEvent auctionEvent = auctionEventBuilder.status(status.code()).errors(errorMessages).build();
        final PrivacyContext privacyContext = auctionContext != null ? auctionContext.getPrivacyContext() : null;
        final TcfContext tcfContext = privacyContext != null ? privacyContext.getTcfContext() : TcfContext.empty();
        respondWith(routingContext, status, body, startTime, requestType, metricRequestStatus, auctionEvent,
                tcfContext);

        httpInteractionLogger.maybeLogOpenrtb2Auction(auctionContext, routingContext, status.code(), body);
    }

    private void respondWith(RoutingContext routingContext, HttpResponseStatus status, String body, long startTime,
                             MetricName requestType, MetricName metricRequestStatus, AuctionEvent event,
                             TcfContext tcfContext) {

        final boolean responseSent = HttpUtil.executeSafely(routingContext, Endpoint.openrtb2_auction,
                response -> response
                        .exceptionHandler(throwable -> handleResponseException(throwable, requestType))
                        .setStatusCode(status.code())
                        .end(body));

        if (responseSent) {
            metrics.updateRequestTimeMetric(MetricName.request_time, clock.millis() - startTime);
            metrics.updateRequestTypeMetric(requestType, metricRequestStatus);
            analyticsDelegator.processEvent(event, tcfContext);
        } else {
            metrics.updateRequestTypeMetric(requestType, MetricName.networkerr);
        }
    }

    private void handleResponseException(Throwable throwable, MetricName requestType) {
        logger.warn("Failed to send auction response: {0}", throwable.getMessage());
        metrics.updateRequestTypeMetric(requestType, MetricName.networkerr);
    }

    private void enrichWithCommonHeaders(HttpServerResponse response) {
        HttpUtil.addHeaderIfValueIsNotEmpty(
                response.headers(), HttpUtil.X_PREBID_HEADER, prebidVersionProvider.getNameVersionRecord());
    }

    private void enrichWithSuccessfulHeaders(HttpServerResponse response) {
        response.headers()
                .add(HttpUtil.CONTENT_TYPE_HEADER, HttpHeaderValues.APPLICATION_JSON);
    }
}
