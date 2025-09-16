package org.prebid.server.handler.openrtb2;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.analytics.reporter.AnalyticsReporterDelegator;
import org.prebid.server.auction.AnalyticsTagsEnricher;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.auction.HookDebugInfoEnricher;
import org.prebid.server.auction.HooksMetricsService;
import org.prebid.server.auction.SkippedAuctionService;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.requestfactory.AuctionRequestFactory;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.exception.BlocklistedAccountException;
import org.prebid.server.exception.BlocklistedAppException;
import org.prebid.server.exception.InvalidAccountConfigException;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.UnauthorizedAccountException;
import org.prebid.server.hooks.execution.HookStageExecutor;
import org.prebid.server.hooks.execution.model.HookStageExecutionResult;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.log.HttpInteractionLogger;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.model.Endpoint;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.version.PrebidVersionProvider;
import org.prebid.server.vertx.verticles.server.HttpEndpoint;
import org.prebid.server.vertx.verticles.server.application.ApplicationResource;

import java.time.Clock;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class AuctionHandler implements ApplicationResource {

    private static final Logger logger = LoggerFactory.getLogger(AuctionHandler.class);
    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(logger);

    private final double logSamplingRate;
    private final AuctionRequestFactory auctionRequestFactory;
    private final ExchangeService exchangeService;
    private final SkippedAuctionService skippedAuctionService;
    private final AnalyticsReporterDelegator analyticsDelegator;
    private final Metrics metrics;
    private final HooksMetricsService hooksMetricsService;
    private final Clock clock;
    private final HttpInteractionLogger httpInteractionLogger;
    private final PrebidVersionProvider prebidVersionProvider;
    private final HookStageExecutor hookStageExecutor;
    private final JacksonMapper mapper;

    public AuctionHandler(double logSamplingRate,
                          AuctionRequestFactory auctionRequestFactory,
                          ExchangeService exchangeService,
                          SkippedAuctionService skippedAuctionService,
                          AnalyticsReporterDelegator analyticsDelegator,
                          Metrics metrics,
                          HooksMetricsService hooksMetricsService,
                          Clock clock,
                          HttpInteractionLogger httpInteractionLogger,
                          PrebidVersionProvider prebidVersionProvider,
                          HookStageExecutor hookStageExecutor,
                          JacksonMapper mapper) {

        this.logSamplingRate = logSamplingRate;
        this.auctionRequestFactory = Objects.requireNonNull(auctionRequestFactory);
        this.exchangeService = Objects.requireNonNull(exchangeService);
        this.skippedAuctionService = Objects.requireNonNull(skippedAuctionService);
        this.analyticsDelegator = Objects.requireNonNull(analyticsDelegator);
        this.metrics = Objects.requireNonNull(metrics);
        this.hooksMetricsService = Objects.requireNonNull(hooksMetricsService);
        this.clock = Objects.requireNonNull(clock);
        this.httpInteractionLogger = Objects.requireNonNull(httpInteractionLogger);
        this.prebidVersionProvider = Objects.requireNonNull(prebidVersionProvider);
        this.hookStageExecutor = Objects.requireNonNull(hookStageExecutor);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public List<HttpEndpoint> endpoints() {
        return Collections.singletonList(HttpEndpoint.of(HttpMethod.POST, Endpoint.openrtb2_auction.value()));
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

        auctionRequestFactory.parseRequest(routingContext, startTime)
                .compose(auctionContext -> skippedAuctionService.skipAuction(auctionContext)
                        .recover(throwable -> holdAuction(auctionEventBuilder, auctionContext)))
                .map(context -> addContextAndBidResponseToEvent(context, auctionEventBuilder, context))
                .map(context -> prepareSuccessfulResponse(context, routingContext))
                .compose(this::invokeExitpointHooks)
                .map(context -> addContextAndBidResponseToEvent(
                        context.getAuctionContext(), auctionEventBuilder, context))
                .onComplete(result -> handleResult(result, auctionEventBuilder, routingContext, startTime));
    }

    private static <R> R addContextAndBidResponseToEvent(AuctionContext context,
                                                         AuctionEvent.AuctionEventBuilder auctionEventBuilder,
                                                         R result) {

        auctionEventBuilder.auctionContext(context);
        auctionEventBuilder.bidResponse(context.getBidResponse());
        return result;
    }

    private Future<AuctionContext> holdAuction(AuctionEvent.AuctionEventBuilder auctionEventBuilder,
                                               AuctionContext auctionContext) {

        return auctionRequestFactory.enrichAuctionContext(auctionContext)
                .map(this::updateAppAndNoCookieAndImpsMetrics)
                // In case of holdAuction Exception and auctionContext is not present below
                .map(context -> addToEvent(context, auctionEventBuilder::auctionContext, context))
                .compose(exchangeService::holdAuction);
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

    private RawResponseContext prepareSuccessfulResponse(AuctionContext auctionContext, RoutingContext routingContext) {
        final MultiMap responseHeaders = getCommonResponseHeaders(routingContext)
                .add(HttpUtil.CONTENT_TYPE_HEADER, HttpHeaderValues.APPLICATION_JSON);

        return RawResponseContext.builder()
                .responseBody(mapper.encodeToString(auctionContext.getBidResponse()))
                .responseHeaders(responseHeaders)
                .auctionContext(auctionContext)
                .build();
    }

    private Future<RawResponseContext> invokeExitpointHooks(RawResponseContext rawResponseContext) {
        final AuctionContext auctionContext = rawResponseContext.getAuctionContext();

        if (auctionContext.isAuctionSkipped()) {
            return Future.succeededFuture(auctionContext)
                    .map(hooksMetricsService::updateHooksMetrics)
                    .map(rawResponseContext);
        }

        return hookStageExecutor.executeExitpointStage(
                        rawResponseContext.getResponseHeaders(),
                        rawResponseContext.getResponseBody(),
                        auctionContext)
                .map(HookStageExecutionResult::getPayload)
                .compose(payload -> Future.succeededFuture(auctionContext)
                        .map(AnalyticsTagsEnricher::enrichWithAnalyticsTags)
                        .map(HookDebugInfoEnricher::enrichWithHooksDebugInfo)
                        .map(hooksMetricsService::updateHooksMetrics)
                        .map(context -> RawResponseContext.builder()
                                .auctionContext(context)
                                .responseHeaders(payload.responseHeaders())
                                .responseBody(payload.responseBody())
                                .build()));
    }

    private void handleResult(AsyncResult<RawResponseContext> responseResult,
                              AuctionEvent.AuctionEventBuilder auctionEventBuilder,
                              RoutingContext routingContext,
                              long startTime) {

        final boolean responseSucceeded = responseResult.succeeded();

        final RawResponseContext rawResponseContext = responseSucceeded ? responseResult.result() : null;
        final AuctionContext auctionContext = rawResponseContext != null
                ? rawResponseContext.getAuctionContext()
                : null;
        final boolean isAuctionSkipped = responseSucceeded && auctionContext.isAuctionSkipped();
        final MetricName requestType = responseSucceeded
                ? auctionContext.getRequestTypeMetric()
                : MetricName.openrtb2web;

        final MetricName metricRequestStatus;
        final List<String> errorMessages;
        final HttpResponseStatus status;
        final String body;

        final HttpServerResponse response = routingContext.response();
        final MultiMap responseHeaders = response.headers();

        if (responseSucceeded) {
            metricRequestStatus = MetricName.ok;
            errorMessages = Collections.emptyList();
            status = HttpResponseStatus.OK;

            rawResponseContext.getResponseHeaders()
                    .forEach(header -> HttpUtil.addHeaderIfValueIsNotEmpty(
                            responseHeaders, header.getKey(), header.getValue()));
            body = rawResponseContext.getResponseBody();
        } else {
            getCommonResponseHeaders(routingContext)
                    .forEach(header -> HttpUtil.addHeaderIfValueIsNotEmpty(
                            responseHeaders, header.getKey(), header.getValue()));

            final Throwable exception = responseResult.cause();
            if (exception instanceof InvalidRequestException invalidRequestException) {
                metricRequestStatus = MetricName.badinput;

                errorMessages = invalidRequestException.getMessages().stream()
                        .map(msg -> "Invalid request format: " + msg)
                        .toList();
                final String message = String.join("\n", errorMessages);
                final String referer = routingContext.request().headers().get(HttpUtil.REFERER_HEADER);
                conditionalLogger.info("%s, Referer: %s".formatted(message, referer), logSamplingRate);

                status = HttpResponseStatus.BAD_REQUEST;
                body = message;
            } else if (exception instanceof UnauthorizedAccountException) {
                metricRequestStatus = MetricName.badinput;
                final String message = exception.getMessage();
                conditionalLogger.info(message, logSamplingRate);
                errorMessages = Collections.singletonList(message);

                status = HttpResponseStatus.UNAUTHORIZED;

                body = message;
            } else if (exception instanceof BlocklistedAppException
                    || exception instanceof BlocklistedAccountException) {
                metricRequestStatus = exception instanceof BlocklistedAccountException
                        ? MetricName.blocklisted_account
                        : MetricName.blocklisted_app;
                final String message = "Blocklisted: " + exception.getMessage();
                logger.debug(message);

                errorMessages = Collections.singletonList(message);
                status = HttpResponseStatus.FORBIDDEN;
                body = message;
            } else if (exception instanceof InvalidAccountConfigException) {
                metricRequestStatus = MetricName.bad_requests;
                final String message = exception.getMessage();
                conditionalLogger.error(exception.getMessage(), logSamplingRate);

                errorMessages = Collections.singletonList(message);
                status = HttpResponseStatus.BAD_REQUEST;
                body = message;
            } else {
                metricRequestStatus = MetricName.err;
                logger.error("Critical error while running the auction", exception);

                final String message = exception.getMessage();
                errorMessages = Collections.singletonList(message);

                status = HttpResponseStatus.INTERNAL_SERVER_ERROR;
                body = "Critical error while running the auction: " + message;
            }
        }

        final AuctionEvent auctionEvent = auctionEventBuilder.status(status.code()).errors(errorMessages).build();
        final PrivacyContext privacyContext = auctionContext != null ? auctionContext.getPrivacyContext() : null;
        final TcfContext tcfContext = privacyContext != null ? privacyContext.getTcfContext() : TcfContext.empty();

        final boolean responseSent = respondWith(routingContext, status, body, requestType);

        if (responseSent) {
            metrics.updateRequestTimeMetric(MetricName.request_time, clock.millis() - startTime);
            metrics.updateRequestTypeMetric(requestType, metricRequestStatus);
            if (!isAuctionSkipped) {
                analyticsDelegator.processEvent(auctionEvent, tcfContext);
            }
        } else {
            metrics.updateRequestTypeMetric(requestType, MetricName.networkerr);
        }

        httpInteractionLogger.maybeLogOpenrtb2Auction(auctionContext, routingContext, status.code(), body);
    }

    private boolean respondWith(RoutingContext routingContext,
                                HttpResponseStatus status,
                                String body,
                                MetricName requestType) {

        return HttpUtil.executeSafely(
                routingContext,
                Endpoint.openrtb2_auction,
                response -> response
                        .exceptionHandler(throwable -> handleResponseException(throwable, requestType))
                        .setStatusCode(status.code())
                        .end(body));

    }

    private void handleResponseException(Throwable throwable, MetricName requestType) {
        logger.warn("Failed to send auction response: {}", throwable.getMessage());
        metrics.updateRequestTypeMetric(requestType, MetricName.networkerr);
    }

    private MultiMap getCommonResponseHeaders(RoutingContext routingContext) {
        final MultiMap responseHeaders = MultiMap.caseInsensitiveMultiMap();
        HttpUtil.addHeaderIfValueIsNotEmpty(
                responseHeaders, HttpUtil.X_PREBID_HEADER, prebidVersionProvider.getNameVersionRecord());

        final MultiMap requestHeaders = routingContext.request().headers();
        if (requestHeaders.contains(HttpUtil.SEC_BROWSING_TOPICS_HEADER)) {
            responseHeaders.add(HttpUtil.OBSERVE_BROWSING_TOPICS_HEADER, "?1");
        }

        return responseHeaders;
    }
}
