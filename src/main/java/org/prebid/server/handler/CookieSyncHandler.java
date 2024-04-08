package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.BooleanUtils;
import org.prebid.server.activity.infrastructure.creator.ActivityInfrastructureCreator;
import org.prebid.server.analytics.model.CookieSyncEvent;
import org.prebid.server.analytics.reporter.AnalyticsReporterDelegator;
import org.prebid.server.auction.gpp.CookieSyncGppService;
import org.prebid.server.auction.privacy.contextfactory.CookieSyncPrivacyContextFactory;
import org.prebid.server.bidder.UsersyncMethodChooser;
import org.prebid.server.cookie.CookieDeprecationService;
import org.prebid.server.cookie.CookieSyncService;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.cookie.exception.CookieSyncException;
import org.prebid.server.cookie.exception.InvalidCookieSyncRequestException;
import org.prebid.server.cookie.exception.UnauthorizedUidsException;
import org.prebid.server.cookie.model.BiddersContext;
import org.prebid.server.cookie.model.CookieSyncContext;
import org.prebid.server.cookie.model.PartitionedCookie;
import org.prebid.server.exception.InvalidAccountConfigException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.metric.Metrics;
import org.prebid.server.model.Endpoint;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.proto.openrtb.ext.request.TraceLevel;
import org.prebid.server.proto.request.CookieSyncRequest;
import org.prebid.server.proto.response.CookieSyncResponse;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.verticles.server.HttpEndpoint;
import org.prebid.server.vertx.verticles.server.application.ApplicationResource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class CookieSyncHandler implements ApplicationResource {

    private static final Logger logger = LoggerFactory.getLogger(CookieSyncHandler.class);
    private static final ConditionalLogger BAD_REQUEST_LOGGER = new ConditionalLogger(logger);

    private final long defaultTimeout;
    private final double logSamplingRate;
    private final UidsCookieService uidsCookieService;
    private final CookieDeprecationService cookieDeprecationService;
    private final CookieSyncGppService gppService;
    private final ActivityInfrastructureCreator activityInfrastructureCreator;
    private final CookieSyncService cookieSyncService;
    private final ApplicationSettings applicationSettings;
    private final CookieSyncPrivacyContextFactory cookieSyncPrivacyContextFactory;
    private final AnalyticsReporterDelegator analyticsDelegator;
    private final Metrics metrics;
    private final TimeoutFactory timeoutFactory;
    private final JacksonMapper mapper;

    public CookieSyncHandler(long defaultTimeout,
                             double logSamplingRate,
                             UidsCookieService uidsCookieService,
                             CookieDeprecationService cookieDeprecationService,
                             CookieSyncGppService gppService,
                             ActivityInfrastructureCreator activityInfrastructureCreator,
                             CookieSyncService cookieSyncService,
                             ApplicationSettings applicationSettings,
                             CookieSyncPrivacyContextFactory cookieSyncPrivacyContextFactory,
                             AnalyticsReporterDelegator analyticsDelegator,
                             Metrics metrics,
                             TimeoutFactory timeoutFactory,
                             JacksonMapper mapper) {

        this.defaultTimeout = defaultTimeout;
        this.logSamplingRate = logSamplingRate;
        this.uidsCookieService = Objects.requireNonNull(uidsCookieService);
        this.cookieDeprecationService = Objects.requireNonNull(cookieDeprecationService);
        this.gppService = Objects.requireNonNull(gppService);
        this.activityInfrastructureCreator = Objects.requireNonNull(activityInfrastructureCreator);
        this.cookieSyncService = Objects.requireNonNull(cookieSyncService);
        this.applicationSettings = Objects.requireNonNull(applicationSettings);
        this.cookieSyncPrivacyContextFactory = Objects.requireNonNull(cookieSyncPrivacyContextFactory);
        this.analyticsDelegator = Objects.requireNonNull(analyticsDelegator);
        this.metrics = Objects.requireNonNull(metrics);
        this.timeoutFactory = Objects.requireNonNull(timeoutFactory);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public List<HttpEndpoint> endpoints() {
        return Collections.singletonList(HttpEndpoint.of(HttpMethod.POST, Endpoint.cookie_sync.value()));
    }

    @Override
    public void handle(RoutingContext routingContext) {
        metrics.updateCookieSyncRequestMetric();

        cookieSyncContext(routingContext)
                .compose(this::fillWithAccount)
                .map(this::fillWithGppContext)
                .map(this::fillWithActivityInfrastructure)
                .map(this::processGpp)
                .compose(this::fillWithPrivacyContext)
                .compose(cookieSyncService::processContext)
                .onFailure(error -> respondWithError(error, routingContext))
                .onSuccess(cookieSyncContext ->
                        respondWithResult(cookieSyncContext, cookieSyncService.prepareResponse(cookieSyncContext)));
    }

    private Future<CookieSyncContext> cookieSyncContext(RoutingContext routingContext) {
        final CookieSyncRequest cookieSyncRequest;
        try {
            cookieSyncRequest = parseRequest(routingContext);
        } catch (Exception e) {
            return Future.failedFuture(e);
        }

        final boolean debug = BooleanUtils.toBoolean(cookieSyncRequest.getDebug());
        final Timeout timeout = timeoutFactory.create(defaultTimeout);
        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(routingContext);
        final BiddersContext biddersContext = BiddersContext.builder().build();

        final CookieSyncContext cookieSyncContext = CookieSyncContext.builder()
                .routingContext(routingContext)
                .uidsCookie(uidsCookie)
                .cookieSyncRequest(cookieSyncRequest)
                .usersyncMethodChooser(UsersyncMethodChooser.from(cookieSyncRequest.getFilterSettings()))
                .biddersContext(biddersContext)
                .timeout(timeout)
                .debug(debug)
                .warnings(new ArrayList<>())
                .build();

        return Future.succeededFuture(cookieSyncContext);
    }

    private CookieSyncRequest parseRequest(RoutingContext routingContext) {
        final Buffer body = routingContext.getBody();
        if (body == null) {
            throw new InvalidCookieSyncRequestException("Request has no body");
        }

        try {
            return mapper.decodeValue(body, CookieSyncRequest.class);
        } catch (DecodeException e) {
            final String message = "Request body cannot be parsed";
            logger.info(message, e);
            throw new InvalidCookieSyncRequestException(message);
        }
    }

    private Future<CookieSyncContext> fillWithAccount(CookieSyncContext cookieSyncContext) {
        return accountById(cookieSyncContext.getCookieSyncRequest().getAccount(), cookieSyncContext.getTimeout())
                .map(cookieSyncContext::with);
    }

    private Future<Account> accountById(String accountId, Timeout timeout) {
        return applicationSettings.getAccountById(accountId, timeout).otherwise(Account.empty(accountId));
    }

    private CookieSyncContext fillWithGppContext(CookieSyncContext cookieSyncContext) {
        return cookieSyncContext.toBuilder()
                .gppContext(gppService.contextFrom(cookieSyncContext))
                .build();
    }

    private CookieSyncContext fillWithActivityInfrastructure(CookieSyncContext cookieSyncContext) {
        return cookieSyncContext.toBuilder()
                .activityInfrastructure(activityInfrastructureCreator.create(
                        cookieSyncContext.getAccount(),
                        cookieSyncContext.getGppContext(),
                        cookieSyncContext.isDebug() ? TraceLevel.verbose : null))
                .build();
    }

    private CookieSyncContext processGpp(CookieSyncContext cookieSyncContext) {
        return cookieSyncContext.with(
                gppService.updateCookieSyncRequest(cookieSyncContext.getCookieSyncRequest(), cookieSyncContext));
    }

    private Future<CookieSyncContext> fillWithPrivacyContext(CookieSyncContext cookieSyncContext) {
        return cookieSyncPrivacyContextFactory.contextFrom(
                        cookieSyncContext.getCookieSyncRequest(),
                        cookieSyncContext.getRoutingContext().request(),
                        cookieSyncContext.getAccount(),
                        cookieSyncContext.getTimeout())
                .map(cookieSyncContext::with);
    }

    private void respondWithResult(CookieSyncContext cookieSyncContext, CookieSyncResponse cookieSyncResponse) {
        final HttpResponseStatus status = HttpResponseStatus.OK;
        final PartitionedCookie deprecationCookie = cookieDeprecationService.makeCookie(
                cookieSyncContext.getAccount(),
                cookieSyncContext.getRoutingContext());

        HttpUtil.executeSafely(cookieSyncContext.getRoutingContext(), Endpoint.cookie_sync,
                response -> {
                    response.setStatusCode(status.code())
                            .putHeader(HttpUtil.CONTENT_TYPE_HEADER, HttpHeaderValues.APPLICATION_JSON);

                    Optional.ofNullable(deprecationCookie)
                            .ifPresent(cookie -> response.putHeader(HttpUtil.SET_COOKIE_HEADER, cookie.encode()));

                    response.end(mapper.encodeToString(cookieSyncResponse));
                });

        final CookieSyncEvent event = CookieSyncEvent.builder()
                .status(status.code())
                .bidderStatus(cookieSyncResponse.getBidderStatus())
                .build();

        analyticsDelegator.processEvent(event, cookieSyncContext.getPrivacyContext().getTcfContext());
    }

    private void respondWithError(Throwable error, RoutingContext routingContext) {
        final TcfContext tcfContext = error instanceof CookieSyncException
                ? ((CookieSyncException) error).tcfContext
                : null;

        final String message = error.getMessage();
        final HttpResponseStatus status;
        final String body;

        if (error instanceof InvalidCookieSyncRequestException) {
            status = HttpResponseStatus.BAD_REQUEST;
            body = "Invalid request format: " + message;

            metrics.updateUserSyncBadRequestMetric();
            BAD_REQUEST_LOGGER.info(message, logSamplingRate);
        } else if (error instanceof UnauthorizedUidsException) {
            status = HttpResponseStatus.UNAUTHORIZED;
            body = "Unauthorized: " + message;

            metrics.updateUserSyncOptoutMetric();
        } else if (error instanceof InvalidAccountConfigException) {
            status = HttpResponseStatus.BAD_REQUEST;
            body = "Invalid account configuration: " + message;

            BAD_REQUEST_LOGGER.info(message, logSamplingRate);
        } else {
            status = HttpResponseStatus.INTERNAL_SERVER_ERROR;
            body = "Unexpected setuid processing error: " + message;

            logger.warn(body, error);
        }

        HttpUtil.executeSafely(routingContext, Endpoint.cookie_sync,
                response -> response
                        .setStatusCode(status.code())
                        .end(body));

        final CookieSyncEvent cookieSyncEvent = CookieSyncEvent.error(status.code(), body);
        if (tcfContext == null) {
            analyticsDelegator.processEvent(cookieSyncEvent);
        } else {
            analyticsDelegator.processEvent(cookieSyncEvent, tcfContext);
        }
    }
}
