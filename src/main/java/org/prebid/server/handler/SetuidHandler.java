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
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.model.SetuidEvent;
import org.prebid.server.auction.PrivacyEnforcementService;
import org.prebid.server.auction.model.SetuidContext;
import org.prebid.server.auction.model.Tuple2;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.exception.InvalidRequestException;
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
    private final AnalyticsReporter analyticsReporter;
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
                         AnalyticsReporter analyticsReporter,
                         Metrics metrics,
                         TimeoutFactory timeoutFactory) {

        this.defaultTimeout = defaultTimeout;
        this.uidsCookieService = Objects.requireNonNull(uidsCookieService);
        this.applicationSettings = Objects.requireNonNull(applicationSettings);
        this.privacyEnforcementService = Objects.requireNonNull(privacyEnforcementService);
        this.tcfDefinerService = Objects.requireNonNull(tcfDefinerService);
        this.gdprHostVendorId = gdprHostVendorId;
        this.analyticsReporter = Objects.requireNonNull(analyticsReporter);
        this.metrics = Objects.requireNonNull(metrics);
        this.timeoutFactory = Objects.requireNonNull(timeoutFactory);

        activeCookieFamilyNames = bidderCatalog.names().stream()
                .filter(bidderCatalog::isActive)
                .map(bidderCatalog::usersyncerByName)
                .map(Usersyncer::getCookieFamilyName)
                .collect(Collectors.toSet());
    }

    @Override
    public void handle(RoutingContext context) {
        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(context);
        if (!uidsCookie.allowsSync()) {
            final int status = HttpResponseStatus.UNAUTHORIZED.code();
            respondWith(context, status, null);
            metrics.updateUserSyncOptoutMetric();
            analyticsReporter.processEvent(SetuidEvent.error(status), TcfContext.empty());
            return;
        }

        final String cookieName = context.request().getParam(BIDDER_PARAM);
        final boolean isCookieNameBlank = StringUtils.isBlank(cookieName);
        if (isCookieNameBlank || !activeCookieFamilyNames.contains(cookieName)) {
            final int status = HttpResponseStatus.BAD_REQUEST.code();
            final String body = "\"bidder\" query param is ";
            respondWith(context, status, body + (isCookieNameBlank ? "required" : "invalid"));
            metrics.updateUserSyncBadRequestMetric();
            analyticsReporter.processEvent(SetuidEvent.error(status), TcfContext.empty());
            return;
        }

        final Set<Integer> vendorIds = Collections.singleton(gdprHostVendorId);

        toSetuidContext(context, uidsCookie)
                .compose(setuidContext -> tcfDefinerService.resultForVendorIds(
                        vendorIds, setuidContext.getPrivacyContext().getTcfContext())
                        .map(hostTcfResponse -> Tuple2.of(hostTcfResponse, setuidContext)))
                .setHandler(asyncResult -> handleResult(asyncResult, context, cookieName));
    }

    private Future<SetuidContext> toSetuidContext(RoutingContext routingContext,
                                                  UidsCookie uidsCookie) {
        final HttpServerRequest httpServerRequest = routingContext.request();
        final String requestAccount = httpServerRequest.getParam(ACCOUNT_PARAM);
        final Timeout timeout = timeoutFactory.create(defaultTimeout);

        return accountById(requestAccount, timeout)
                .compose(account -> privacyEnforcementService.contextFromSetuidRequest(
                        httpServerRequest, account, timeout)
                        .map(privacyContext -> SetuidContext.builder()
                                .routingContext(routingContext)
                                .uidsCookie(uidsCookie)
                                .timeout(timeout)
                                .account(account)
                                .privacyContext(privacyContext)
                                .build()));
    }

    private Future<Account> accountById(String accountId, Timeout timeout) {
        return StringUtils.isBlank(accountId)
                ? Future.succeededFuture(Account.empty(accountId))
                : applicationSettings.getAccountById(accountId, timeout)
                .otherwise(Account.empty(accountId));
    }

    private void handleResult(
            AsyncResult<Tuple2<TcfResponse<Integer>, SetuidContext>> hostTcfResponseToSetuidContextResult,
            RoutingContext routingContext,
            String bidder) {
        if (hostTcfResponseToSetuidContextResult.failed()) {
            final Throwable failCause = hostTcfResponseToSetuidContextResult.cause();
            respondWithError(routingContext, bidder, failCause);
        } else {
            // allow cookie only if user is not in GDPR scope or vendor passed GDPR check
            final Tuple2<TcfResponse<Integer>, SetuidContext> hostTcfResponseAndTcfContext =
                    hostTcfResponseToSetuidContextResult.result();
            final TcfResponse<Integer> tcfResponse = hostTcfResponseAndTcfContext.getLeft();

            final boolean notInGdprScope = BooleanUtils.isFalse(tcfResponse.getUserInGdprScope());

            final Map<Integer, PrivacyEnforcementAction> vendorIdToAction = tcfResponse.getActions();
            final PrivacyEnforcementAction hostPrivacyAction = vendorIdToAction != null
                    ? vendorIdToAction.get(gdprHostVendorId)
                    : null;
            final boolean blockPixelSync = hostPrivacyAction == null || hostPrivacyAction.isBlockPixelSync();

            final boolean allowedCookie = notInGdprScope || !blockPixelSync;

            final SetuidContext setuidContext = hostTcfResponseAndTcfContext.getRight();
            if (allowedCookie) {
                respondWithCookie(setuidContext, bidder);
            } else {
                final TcfContext tcfContext = setuidContext.getPrivacyContext().getTcfContext();
                respondWithoutCookie(routingContext, HttpResponseStatus.OK.code(),
                        "The gdpr_consent param prevents cookies from being saved", bidder, tcfContext);
            }
        }
    }

    private void respondWithError(RoutingContext context, String bidder, Throwable exception) {
        final int status;
        final String body;

        if (exception instanceof InvalidRequestException) {
            status = HttpResponseStatus.BAD_REQUEST.code();
            body = String.format("GDPR processing failed with error: %s", exception.getMessage());
        } else {
            status = HttpResponseStatus.INTERNAL_SERVER_ERROR.code();
            body = "Unexpected GDPR processing error";
            logger.warn(body, exception);
        }

        respondWithoutCookie(context, status, body, bidder, TcfContext.empty());
    }

    private void respondWithoutCookie(RoutingContext routingContext,
                                      int status,
                                      String body,
                                      String bidder,
                                      TcfContext tcfContext) {
        respondWith(routingContext, status, body);
        metrics.updateUserSyncTcfBlockedMetric(bidder);

        analyticsReporter.processEvent(SetuidEvent.error(status), tcfContext);
    }

    private void respondWithCookie(SetuidContext setuidContext,
                                   String bidder) {
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
        analyticsReporter.processEvent(SetuidEvent.builder()
                .status(status)
                .bidder(bidder)
                .uid(uid)
                .success(successfullyUpdated)
                .build(), tcfContext);
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
