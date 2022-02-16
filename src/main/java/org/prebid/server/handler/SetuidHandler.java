package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import lombok.Value;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.analytics.model.SetuidEvent;
import org.prebid.server.analytics.reporter.AnalyticsReporterDelegator;
import org.prebid.server.auction.PrivacyEnforcementService;
import org.prebid.server.auction.model.SetuidContext;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.UsersyncUtil;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.UnauthorizedUidsException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.metric.Metrics;
import org.prebid.server.model.Endpoint;
import org.prebid.server.privacy.gdpr.TcfDefinerService;
import org.prebid.server.privacy.gdpr.model.HostVendorTcfResponse;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.gdpr.model.TcfResponse;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;
import org.prebid.server.util.HttpUtil;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class SetuidHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(SetuidHandler.class);

    private static final String BIDDER_PARAM = "bidder";
    private static final String UID_PARAM = "uid";
    private static final String PIXEL_FILE_PATH = "static/tracking-pixel.png";
    private static final String ACCOUNT_PARAM = "account";
    private static final int UNAVAILABLE_FOR_LEGAL_REASONS = 451;

    private final long defaultTimeout;
    private final UidsCookieService uidsCookieService;
    private final ApplicationSettings applicationSettings;
    private final PrivacyEnforcementService privacyEnforcementService;
    private final TcfDefinerService tcfDefinerService;
    private final Integer gdprHostVendorId;
    private final AnalyticsReporterDelegator analyticsDelegator;
    private final Metrics metrics;
    private final TimeoutFactory timeoutFactory;
    private final Map<String, String> cookieNameToSyncType;

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

        cookieNameToSyncType = bidderCatalog.names().stream()
                .filter(bidderCatalog::isActive)
                .map(bidderCatalog::usersyncerByName)
                .distinct() // built-in aliases looks like bidders with the same usersyncers
                .collect(Collectors.toMap(Usersyncer::getCookieFamilyName, SetuidHandler::preferredUserSyncType));
    }

    private static Integer validateHostVendorId(Integer gdprHostVendorId) {
        if (gdprHostVendorId == null) {
            logger.warn("gdpr.host-vendor-id not specified. Will skip host company GDPR checks");
        }
        return gdprHostVendorId;
    }

    private static String preferredUserSyncType(Usersyncer usersyncer) {
        return usersyncer.getPrimaryMethod().getType();
    }

    @Override
    public void handle(RoutingContext routingContext) {
        toSetuidContext(routingContext)
                .onComplete(setuidContextResult -> handleSetuidContextResult(setuidContextResult, routingContext));
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
                                .syncType(cookieNameToSyncType.get(cookieName))
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
            final String bidder = setuidContext.getCookieName();
            final TcfContext tcfContext = setuidContext.getPrivacyContext().getTcfContext();

            try {
                validateSetuidContext(setuidContext, bidder);
            } catch (InvalidRequestException | UnauthorizedUidsException e) {
                handleErrors(e, routingContext, tcfContext);
                return;
            }

            isAllowedForHostVendorId(tcfContext)
                    .onComplete(hostTcfResponseResult -> respondByTcfResponse(hostTcfResponseResult, setuidContext));
        } else {
            final Throwable error = setuidContextResult.cause();
            handleErrors(error, routingContext, null);
        }
    }

    private void validateSetuidContext(SetuidContext setuidContext, String bidder) {
        final String cookieName = setuidContext.getCookieName();
        final boolean isCookieNameBlank = StringUtils.isBlank(cookieName);
        if (isCookieNameBlank || !cookieNameToSyncType.containsKey(cookieName)) {
            final String cookieNameError = isCookieNameBlank ? "required" : "invalid";
            throw new InvalidRequestException(String.format("\"bidder\" query param is %s", cookieNameError));
        }

        final TcfContext tcfContext = setuidContext.getPrivacyContext().getTcfContext();
        if (StringUtils.equals(tcfContext.getGdpr(), "1") && BooleanUtils.isFalse(tcfContext.getIsConsentValid())) {
            metrics.updateUserSyncTcfInvalidMetric(bidder);
            throw new InvalidRequestException("Consent string is invalid");
        }

        final UidsCookie uidsCookie = setuidContext.getUidsCookie();
        if (!uidsCookie.allowsSync()) {
            throw new UnauthorizedUidsException("Sync is not allowed for this uids");
        }
    }

    /**
     * If host vendor id is null, host allowed to setuid.
     */
    private Future<HostVendorTcfResponse> isAllowedForHostVendorId(TcfContext tcfContext) {
        return gdprHostVendorId == null
                ? Future.succeededFuture(HostVendorTcfResponse.allowedVendor())
                : tcfDefinerService.resultForVendorIds(Collections.singleton(gdprHostVendorId), tcfContext)
                .map(this::toHostVendorTcfResponse);
    }

    private HostVendorTcfResponse toHostVendorTcfResponse(TcfResponse<Integer> tcfResponse) {
        return HostVendorTcfResponse.of(tcfResponse.getUserInGdprScope(), tcfResponse.getCountry(),
                isSetuidAllowed(tcfResponse));
    }

    private boolean isSetuidAllowed(TcfResponse<Integer> hostTcfResponseToSetuidContext) {
        // allow cookie only if user is not in GDPR scope or vendor passed GDPR check
        final boolean notInGdprScope = BooleanUtils.isFalse(hostTcfResponseToSetuidContext.getUserInGdprScope());

        final Map<Integer, PrivacyEnforcementAction> vendorIdToAction = hostTcfResponseToSetuidContext.getActions();
        final PrivacyEnforcementAction hostPrivacyAction = vendorIdToAction != null
                ? vendorIdToAction.get(gdprHostVendorId)
                : null;
        final boolean blockPixelSync = hostPrivacyAction == null || hostPrivacyAction.isBlockPixelSync();

        return notInGdprScope || !blockPixelSync;
    }

    private void respondByTcfResponse(AsyncResult<HostVendorTcfResponse> hostTcfResponseResult,
                                      SetuidContext setuidContext) {
        final String bidderCookieName = setuidContext.getCookieName();
        final TcfContext tcfContext = setuidContext.getPrivacyContext().getTcfContext();
        final RoutingContext routingContext = setuidContext.getRoutingContext();

        if (hostTcfResponseResult.succeeded()) {
            final HostVendorTcfResponse hostTcfResponse = hostTcfResponseResult.result();
            if (hostTcfResponse.isVendorAllowed()) {
                respondWithCookie(setuidContext);
            } else {
                metrics.updateUserSyncTcfBlockedMetric(bidderCookieName);

                final HttpResponseStatus status = new HttpResponseStatus(UNAVAILABLE_FOR_LEGAL_REASONS,
                        "Unavailable for legal reasons");

                HttpUtil.executeSafely(routingContext, Endpoint.setuid,
                        response -> response
                                .setStatusCode(status.code())
                                .setStatusMessage(status.reasonPhrase())
                                .end("The gdpr_consent param prevents cookies from being saved"));

                analyticsDelegator.processEvent(SetuidEvent.error(status.code()), tcfContext);
            }

        } else {
            final Throwable error = hostTcfResponseResult.cause();
            metrics.updateUserSyncTcfBlockedMetric(bidderCookieName);
            handleErrors(error, routingContext, tcfContext);
        }
    }

    private void respondWithCookie(SetuidContext setuidContext) {
        final RoutingContext routingContext = setuidContext.getRoutingContext();
        final String uid = routingContext.request().getParam(UID_PARAM);
        final String bidder = setuidContext.getCookieName();

        final UidsCookieUpdateResult uidsCookieUpdateResult = updateUidsCookie(
                setuidContext.getUidsCookie(), bidder, uid);

        final Cookie updatedUidsCookie = uidsCookieService.toCookie(uidsCookieUpdateResult.getUidsCookie());
        addCookie(routingContext, updatedUidsCookie);

        final int statusCode = HttpResponseStatus.OK.code();
        HttpUtil.executeSafely(routingContext, Endpoint.setuid, buildCookieResponseConsumer(setuidContext, statusCode));

        final TcfContext tcfContext = setuidContext.getPrivacyContext().getTcfContext();
        final SetuidEvent setuidEvent = SetuidEvent.builder()
                .status(statusCode)
                .bidder(bidder)
                .uid(uid)
                .success(uidsCookieUpdateResult.isSuccessfullyUpdated())
                .build();
        analyticsDelegator.processEvent(setuidEvent, tcfContext);
    }

    private UidsCookieUpdateResult updateUidsCookie(UidsCookie uidsCookie, String bidderName, String uid) {
        if (StringUtils.isBlank(uid)) {
            return UidsCookieUpdateResult.unaltered(uidsCookie.deleteUid(bidderName));
        } else if (UidsCookie.isFacebookSentinel(bidderName, uid)) {
            // At the moment, Facebook calls /setuid with a UID of 0 if the user isn't logged into Facebook.
            // They shouldn't be sending us a sentinel value... but since they are, we're refusing to save that ID.
            return UidsCookieUpdateResult.unaltered(uidsCookie);
        } else {
            final UidsCookie updatedCookie = uidsCookie.updateUid(bidderName, uid);
            metrics.updateUserSyncSetsMetric(bidderName);
            return UidsCookieUpdateResult.updated(updatedCookie);
        }
    }

    private Consumer<HttpServerResponse> buildCookieResponseConsumer(SetuidContext setuidContext,
                                                                     int responseStatusCode) {

        final String format = setuidContext.getRoutingContext().request().getParam(UsersyncUtil.FORMAT_PARAMETER);
        return shouldRespondWithPixel(format, setuidContext.getSyncType())
                ? response -> response.sendFile(PIXEL_FILE_PATH)
                : response -> response
                .setStatusCode(responseStatusCode)
                .putHeader(HttpHeaders.CONTENT_LENGTH, "0")
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaders.TEXT_HTML)
                .end();
    }

    private boolean shouldRespondWithPixel(String format, String syncType) {
        return StringUtils.equals(format, UsersyncUtil.IMG_FORMAT)
                || (!StringUtils.equals(format, UsersyncUtil.BLANK_FORMAT)
                && StringUtils.equals(syncType, Usersyncer.UsersyncMethod.REDIRECT_TYPE));
    }

    private void handleErrors(Throwable error, RoutingContext routingContext, TcfContext tcfContext) {
        final String message = error.getMessage();
        final HttpResponseStatus status;
        final String body;
        if (error instanceof InvalidRequestException) {
            metrics.updateUserSyncBadRequestMetric();
            status = HttpResponseStatus.BAD_REQUEST;
            body = String.format("Invalid request format: %s", message);
        } else if (error instanceof UnauthorizedUidsException) {
            metrics.updateUserSyncOptoutMetric();
            status = HttpResponseStatus.UNAUTHORIZED;
            body = String.format("Unauthorized: %s", message);
        } else {
            status = HttpResponseStatus.INTERNAL_SERVER_ERROR;
            body = String.format("Unexpected setuid processing error: %s", message);
            logger.warn(body, error);
        }

        HttpUtil.executeSafely(routingContext, Endpoint.setuid,
                response -> response
                        .setStatusCode(status.code())
                        .end(body));

        final SetuidEvent setuidEvent = SetuidEvent.error(status.code());
        if (tcfContext == null) {
            analyticsDelegator.processEvent(setuidEvent);
        } else {
            analyticsDelegator.processEvent(setuidEvent, tcfContext);
        }
    }

    private void addCookie(RoutingContext routingContext, Cookie cookie) {
        routingContext.response().headers().add(HttpUtil.SET_COOKIE_HEADER, HttpUtil.toSetCookieHeaderValue(cookie));
    }

    @Value(staticConstructor = "of")
    private static class UidsCookieUpdateResult {

        private static UidsCookieUpdateResult updated(UidsCookie uidsCookie) {
            return of(true, uidsCookie);
        }

        private static UidsCookieUpdateResult unaltered(UidsCookie uidsCookie) {
            return of(false, uidsCookie);
        }

        boolean successfullyUpdated;

        UidsCookie uidsCookie;
    }
}
