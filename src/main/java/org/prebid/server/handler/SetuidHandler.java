package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.analytics.AnalyticsReporterDelegator;
import org.prebid.server.analytics.model.SetuidEvent;
import org.prebid.server.auction.PrivacyEnforcementService;
import org.prebid.server.auction.model.SetuidContext;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.UnauthorizedUidsException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.gdpr.TcfDefinerService;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.gdpr.model.TcfResponse;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;
import org.prebid.server.util.HttpUtil;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class SetuidHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(SetuidHandler.class);

    private static final String BIDDER_PARAM = "bidder";
    private static final String UID_PARAM = "uid";
    private static final String FORMAT_PARAM = "format";
    private static final String IMG_FORMAT_PARAM = "img";
    private static final String PIXEL_FILE_PATH = "static/tracking-pixel.png";
    private static final String ACCOUNT_PARAM = "account";

    private final long defaultTimeout;
    private final UidsCookieService uidsCookieService;
    private final ApplicationSettings applicationSettings;
    private final PrivacyEnforcementService privacyEnforcementService;
    private final TcfDefinerService tcfDefinerService;
    private final Integer gdprHostVendorId;
    private final AnalyticsReporterDelegator analyticsDelegator;
    private final Metrics metrics;
    private final TimeoutFactory timeoutFactory;
    private final Set<String> activeCookieFamilyNames;

    public SetuidHandler(long defaultTimeout,
                         UidsCookieService uidsCookieService,
                         ApplicationSettings applicationSettings,
                         BidderCatalog bidderCatalog,
                         PrivacyEnforcementService privacyEnforcementService,
                         TcfDefinerService tcfDefinerService,
                         Integer gdprHostVendorId,
                         AnalyticsReporterDelegator analyticsDelegator,
                         Metrics metrics,
                         TimeoutFactory timeoutFactory) {

        this.defaultTimeout = defaultTimeout;
        this.uidsCookieService = Objects.requireNonNull(uidsCookieService);
        this.applicationSettings = Objects.requireNonNull(applicationSettings);
        this.privacyEnforcementService = Objects.requireNonNull(privacyEnforcementService);
        this.tcfDefinerService = Objects.requireNonNull(tcfDefinerService);
        this.gdprHostVendorId = validateHostVendorId(gdprHostVendorId);
        this.analyticsDelegator = Objects.requireNonNull(analyticsDelegator);
        this.metrics = Objects.requireNonNull(metrics);
        this.timeoutFactory = Objects.requireNonNull(timeoutFactory);

        activeCookieFamilyNames = bidderCatalog.names().stream()
                .filter(bidderCatalog::isActive)
                .map(bidderCatalog::usersyncerByName)
                .map(Usersyncer::getCookieFamilyName)
                .collect(Collectors.toSet());
    }

    private static Integer validateHostVendorId(Integer gdprHostVendorId) {
        if (gdprHostVendorId == null) {
            logger.warn("gdpr.host-vendor-id not specified. Will skip host company GDPR checks");
        }
        return gdprHostVendorId;
    }

    @Override
    public void handle(RoutingContext context) {
        toSetuidContext(context)
                .setHandler(setuidContextResult -> handleSetuidContextResult(setuidContextResult, context));
    }

    private Future<SetuidContext> toSetuidContext(RoutingContext routingContext) {
        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(routingContext);
        final HttpServerRequest httpRequest = routingContext.request();
        final String cookieName = httpRequest.getParam(BIDDER_PARAM);
        final String requestAccount = httpRequest.getParam(ACCOUNT_PARAM);
        final Timeout timeout = timeoutFactory.create(defaultTimeout);

        return accountById(requestAccount, timeout)
                .compose(account -> privacyEnforcementService.contextFromSetuidRequest(httpRequest, account, timeout)
                        .map(privacyContext -> SetuidContext.builder()
                                .routingContext(routingContext)
                                .uidsCookie(uidsCookie)
                                .timeout(timeout)
                                .account(account)
                                .cookieName(cookieName)
                                .privacyContext(privacyContext)
                                .build()));
    }

    private Future<Account> accountById(String accountId, Timeout timeout) {
        return StringUtils.isBlank(accountId)
                ? Future.succeededFuture(Account.empty(accountId))
                : applicationSettings.getAccountById(accountId, timeout)
                .otherwise(Account.empty(accountId));
    }

    private void handleSetuidContextResult(AsyncResult<SetuidContext> setuidContextResult,
                                           RoutingContext routingContext) {
        if (setuidContextResult.succeeded()) {
            final SetuidContext setuidContext = setuidContextResult.result();
            final TcfContext tcfContext = setuidContext.getPrivacyContext().getTcfContext();
            final Exception exception = validateSetuidContext(setuidContext);
            if (exception != null) {
                handleErrors(exception, routingContext, tcfContext);
                return;
            }

            isAllowedForHostVendorId(tcfContext)
                    .setHandler(
                            isCookieAllowedResult -> respondByVendorIdsResult(isCookieAllowedResult, setuidContext));

        } else {
            final Throwable error = setuidContextResult.cause();
            handleErrors(error, routingContext, null);
        }
    }

    private Exception validateSetuidContext(SetuidContext setuidContext) {
        final String cookieName = setuidContext.getCookieName();
        final boolean isCookieNameBlank = StringUtils.isBlank(cookieName);
        if (isCookieNameBlank || !activeCookieFamilyNames.contains(cookieName)) {
            final String cookieNameError = isCookieNameBlank ? "required" : "invalid";
            return new InvalidRequestException(String.format("\"bidder\" query param is %s", cookieNameError));
        }

        final UidsCookie uidsCookie = setuidContext.getUidsCookie();
        if (!uidsCookie.allowsSync()) {
            return new UnauthorizedUidsException("Sync is not allowed for this uids");
        }

        return null;
    }

    private Future<Boolean> isAllowedForHostVendorId(TcfContext tcfContext) {
        return gdprHostVendorId == null
                ? Future.succeededFuture(true)
                : tcfDefinerService.resultForVendorIds(Collections.singleton(gdprHostVendorId), tcfContext)
                .map(this::isCookieAllowed);
    }

    private Boolean isCookieAllowed(TcfResponse<Integer> hostTcfResponseToSetuidContext) {
        // allow cookie only if user is not in GDPR scope or vendor passed GDPR check
        final boolean notInGdprScope = BooleanUtils.isFalse(hostTcfResponseToSetuidContext.getUserInGdprScope());

        final Map<Integer, PrivacyEnforcementAction> vendorIdToAction = hostTcfResponseToSetuidContext.getActions();
        final PrivacyEnforcementAction hostPrivacyAction = vendorIdToAction != null
                ? vendorIdToAction.get(gdprHostVendorId)
                : null;
        final boolean blockPixelSync = hostPrivacyAction == null || hostPrivacyAction.isBlockPixelSync();

        return notInGdprScope || !blockPixelSync;
    }

    private void respondByVendorIdsResult(AsyncResult<Boolean> isCookieAllowedResult,
                                          SetuidContext setuidContext) {
        final String bidderCookieName = setuidContext.getCookieName();
        final TcfContext tcfContext = setuidContext.getPrivacyContext().getTcfContext();
        final RoutingContext routingContext = setuidContext.getRoutingContext();

        if (isCookieAllowedResult.succeeded()) {
            final Boolean isCookieAllowed = isCookieAllowedResult.result();
            if (isCookieAllowed) {
                respondWithCookie(setuidContext, bidderCookieName);
            } else {
                metrics.updateUserSyncTcfBlockedMetric(bidderCookieName);

                final int status = HttpResponseStatus.OK.code();
                respondWith(routingContext, status, "The gdpr_consent param prevents cookies from being saved");
                analyticsDelegator.processEvent(SetuidEvent.error(status), tcfContext);
            }

        } else {
            final Throwable error = isCookieAllowedResult.cause();
            metrics.updateUserSyncTcfBlockedMetric(bidderCookieName);
            handleErrors(error, routingContext, tcfContext);
        }
    }

    private void respondWithCookie(SetuidContext setuidContext, String bidder) {
        final RoutingContext routingContext = setuidContext.getRoutingContext();
        final String uid = routingContext.request().getParam(UID_PARAM);
        final UidsCookie updatedUidsCookie;
        boolean successfullyUpdated = false;

        final UidsCookie uidsCookie = setuidContext.getUidsCookie();
        if (StringUtils.isBlank(uid)) {
            updatedUidsCookie = uidsCookie.deleteUid(bidder);
        } else if (UidsCookie.isFacebookSentinel(bidder, uid)) {
            // At the moment, Facebook calls /setuid with a UID of 0 if the user isn't logged into Facebook.
            // They shouldn't be sending us a sentinel value... but since they are, we're refusing to save that ID.
            updatedUidsCookie = uidsCookie;
        } else {
            updatedUidsCookie = uidsCookie.updateUid(bidder, uid);
            successfullyUpdated = true;
            metrics.updateUserSyncSetsMetric(bidder);
        }

        final Cookie cookie = uidsCookieService.toCookie(updatedUidsCookie);
        addCookie(routingContext, cookie);

        final int status = HttpResponseStatus.OK.code();

        // Send pixel file to response if "format=img"
        final String format = routingContext.request().getParam(FORMAT_PARAM);
        if (StringUtils.equals(format, IMG_FORMAT_PARAM)) {
            routingContext.response().sendFile(PIXEL_FILE_PATH);
        } else {
            respondWith(routingContext, status, null);
        }

        final TcfContext tcfContext = setuidContext.getPrivacyContext().getTcfContext();
        analyticsDelegator.processEvent(SetuidEvent.builder()
                .status(status)
                .bidder(bidder)
                .uid(uid)
                .success(successfullyUpdated)
                .build(), tcfContext);
    }

    private void handleErrors(Throwable error, RoutingContext routingContext, TcfContext tcfContext) {
        final String message = error.getMessage();
        final int status;
        final String body;
        if (error instanceof InvalidRequestException) {
            metrics.updateUserSyncBadRequestMetric();
            status = HttpResponseStatus.BAD_REQUEST.code();
            body = String.format("Invalid request format: %s", message);

        } else if (error instanceof UnauthorizedUidsException) {
            metrics.updateUserSyncOptoutMetric();
            status = HttpResponseStatus.UNAUTHORIZED.code();
            body = String.format("Unauthorized: %s", message);
        } else {
            status = HttpResponseStatus.INTERNAL_SERVER_ERROR.code();
            body = String.format("Unexpected setuid processing error: %s", message);
            logger.warn(body, error);
        }

        respondWith(routingContext, status, body);
        if (tcfContext == null) {
            analyticsDelegator.processEvent(SetuidEvent.error(status));
        } else {
            analyticsDelegator.processEvent(SetuidEvent.error(status), tcfContext);
        }
    }

    private void addCookie(RoutingContext context, Cookie cookie) {
        context.response().headers().add(HttpUtil.SET_COOKIE_HEADER, HttpUtil.toSetCookieHeaderValue(cookie));
    }

    private static void respondWith(RoutingContext context, int status, String body) {
        // don't send the response if client has gone
        if (context.response().closed()) {
            logger.warn("The client already closed connection, response will be skipped");
            return;
        }

        context.response().setStatusCode(status);
        if (body != null) {
            context.response().end(body);
        } else {
            context.response().end();
        }
    }
}
