package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
import org.prebid.server.activity.infrastructure.creator.ActivityInfrastructureCreator;
import org.prebid.server.activity.infrastructure.payload.ActivityInvocationPayload;
import org.prebid.server.activity.infrastructure.payload.impl.ActivityInvocationPayloadImpl;
import org.prebid.server.activity.infrastructure.payload.impl.TcfContextActivityInvocationPayload;
import org.prebid.server.analytics.model.SetuidEvent;
import org.prebid.server.analytics.reporter.AnalyticsReporterDelegator;
import org.prebid.server.auction.gpp.SetuidGppService;
import org.prebid.server.auction.model.SetuidContext;
import org.prebid.server.auction.privacy.contextfactory.SetuidPrivacyContextFactory;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.UsersyncFormat;
import org.prebid.server.bidder.UsersyncMethod;
import org.prebid.server.bidder.UsersyncMethodType;
import org.prebid.server.bidder.UsersyncUtil;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.cookie.exception.UnauthorizedUidsException;
import org.prebid.server.cookie.exception.UnavailableForLegalReasonsException;
import org.prebid.server.cookie.model.UidsCookieUpdateResult;
import org.prebid.server.exception.InvalidAccountConfigException;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.metric.Metrics;
import org.prebid.server.model.Endpoint;
import org.prebid.server.privacy.HostVendorTcfDefinerService;
import org.prebid.server.privacy.gdpr.model.HostVendorTcfResponse;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.gdpr.model.TcfResponse;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.verticles.server.HttpEndpoint;
import org.prebid.server.vertx.verticles.server.application.ApplicationResource;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SetuidHandler implements ApplicationResource {

    private static final Logger logger = LoggerFactory.getLogger(SetuidHandler.class);

    private static final String BIDDER_PARAM = "bidder";
    private static final String UID_PARAM = "uid";
    private static final String PIXEL_FILE_PATH = "static/tracking-pixel.png";
    private static final String ACCOUNT_PARAM = "account";
    private static final int UNAVAILABLE_FOR_LEGAL_REASONS = 451;

    private final long defaultTimeout;
    private final UidsCookieService uidsCookieService;
    private final ApplicationSettings applicationSettings;
    private final SetuidPrivacyContextFactory setuidPrivacyContextFactory;
    private final SetuidGppService gppService;
    private final ActivityInfrastructureCreator activityInfrastructureCreator;
    private final HostVendorTcfDefinerService tcfDefinerService;
    private final AnalyticsReporterDelegator analyticsDelegator;
    private final Metrics metrics;
    private final TimeoutFactory timeoutFactory;
    private final Map<String, UsersyncMethodType> cookieNameToSyncType;

    public SetuidHandler(long defaultTimeout,
                         UidsCookieService uidsCookieService,
                         ApplicationSettings applicationSettings,
                         BidderCatalog bidderCatalog,
                         SetuidPrivacyContextFactory setuidPrivacyContextFactory,
                         SetuidGppService gppService,
                         ActivityInfrastructureCreator activityInfrastructureCreator,
                         HostVendorTcfDefinerService tcfDefinerService,
                         AnalyticsReporterDelegator analyticsDelegator,
                         Metrics metrics,
                         TimeoutFactory timeoutFactory) {

        this.defaultTimeout = defaultTimeout;
        this.uidsCookieService = Objects.requireNonNull(uidsCookieService);
        this.applicationSettings = Objects.requireNonNull(applicationSettings);
        this.setuidPrivacyContextFactory = Objects.requireNonNull(setuidPrivacyContextFactory);
        this.gppService = Objects.requireNonNull(gppService);
        this.activityInfrastructureCreator = Objects.requireNonNull(activityInfrastructureCreator);
        this.tcfDefinerService = Objects.requireNonNull(tcfDefinerService);
        this.analyticsDelegator = Objects.requireNonNull(analyticsDelegator);
        this.metrics = Objects.requireNonNull(metrics);
        this.timeoutFactory = Objects.requireNonNull(timeoutFactory);
        this.cookieNameToSyncType = collectMap(bidderCatalog);
    }

    private static Map<String, UsersyncMethodType> collectMap(BidderCatalog bidderCatalog) {

        final Supplier<Stream<Usersyncer>> usersyncers = () -> bidderCatalog.names()
                .stream()
                .filter(bidderCatalog::isActive)
                .map(bidderCatalog::usersyncerByName)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .distinct();

        validateUsersyncers(usersyncers.get());

        return usersyncers.get()
                .collect(Collectors.toMap(Usersyncer::getCookieFamilyName, SetuidHandler::preferredUserSyncType));
    }

    @Override
    public List<HttpEndpoint> endpoints() {
        return Collections.singletonList(HttpEndpoint.of(HttpMethod.GET, Endpoint.setuid.value()));
    }

    private static UsersyncMethodType preferredUserSyncType(Usersyncer usersyncer) {
        return Stream.of(usersyncer.getIframe(), usersyncer.getRedirect())
                .filter(Objects::nonNull)
                .findFirst()
                .map(UsersyncMethod::getType)
                .get(); // when usersyncer is present, it will contain at least one method
    }

    private static void validateUsersyncers(Stream<Usersyncer> usersyncers) {
        final List<String> cookieFamilyNameDuplicates = usersyncers.map(Usersyncer::getCookieFamilyName)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()))
                .entrySet()
                .stream()
                .filter(name -> name.getValue() > 1)
                .map(Map.Entry::getKey)
                .distinct()
                .toList();
        if (CollectionUtils.isNotEmpty(cookieFamilyNameDuplicates)) {
            throw new IllegalArgumentException(
                    "Duplicated \"cookie-family-name\" found, values: "
                            + String.join(", ", cookieFamilyNameDuplicates));
        }
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
                .compose(account -> setuidPrivacyContextFactory.contextFrom(httpRequest, account, timeout)
                        .map(privacyContext -> SetuidContext.builder()
                                .routingContext(routingContext)
                                .uidsCookie(uidsCookie)
                                .timeout(timeout)
                                .account(account)
                                .cookieName(cookieName)
                                .syncType(cookieNameToSyncType.get(cookieName))
                                .privacyContext(privacyContext)
                                .build()))

                .compose(setuidContext -> gppService.contextFrom(setuidContext)
                        .map(setuidContext::with))

                .map(this::fillWithActivityInfrastructure)

                .map(gppService::updateSetuidContext);
    }

    private Future<Account> accountById(String accountId, Timeout timeout) {
        return applicationSettings.getAccountById(accountId, timeout).otherwise(Account.empty(accountId));
    }

    private SetuidContext fillWithActivityInfrastructure(SetuidContext setuidContext) {
        return setuidContext.toBuilder()
                .activityInfrastructure(activityInfrastructureCreator.create(
                        setuidContext.getAccount(),
                        setuidContext.getGppContext(),
                        null))
                .build();
    }

    private void handleSetuidContextResult(AsyncResult<SetuidContext> setuidContextResult,
                                           RoutingContext routingContext) {

        if (setuidContextResult.succeeded()) {
            final SetuidContext setuidContext = setuidContextResult.result();
            final String bidder = setuidContext.getCookieName();
            final TcfContext tcfContext = setuidContext.getPrivacyContext().getTcfContext();

            try {
                validateSetuidContext(setuidContext, bidder);
            } catch (InvalidRequestException | UnauthorizedUidsException | UnavailableForLegalReasonsException e) {
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
            throw new InvalidRequestException("\"bidder\" query param is " + cookieNameError);
        }

        final TcfContext tcfContext = setuidContext.getPrivacyContext().getTcfContext();
        if (tcfContext.isInGdprScope() && !tcfContext.isConsentValid()) {
            metrics.updateUserSyncTcfInvalidMetric(bidder);
            throw new InvalidRequestException("Consent string is invalid");
        }

        final UidsCookie uidsCookie = setuidContext.getUidsCookie();
        if (!uidsCookie.allowsSync()) {
            throw new UnauthorizedUidsException("Sync is not allowed for this uids", tcfContext);
        }

        final ActivityInfrastructure activityInfrastructure = setuidContext.getActivityInfrastructure();
        final ActivityInvocationPayload activityInvocationPayload = TcfContextActivityInvocationPayload.of(
                ActivityInvocationPayloadImpl.of(ComponentType.BIDDER, bidder),
                tcfContext);

        if (!activityInfrastructure.isAllowed(Activity.SYNC_USER, activityInvocationPayload)) {
            throw new UnavailableForLegalReasonsException();
        }
    }

    /**
     * If host vendor id is null, host allowed to setuid.
     */
    private Future<HostVendorTcfResponse> isAllowedForHostVendorId(TcfContext tcfContext) {
        final Integer gdprHostVendorId = tcfDefinerService.getGdprHostVendorId();
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
                ? vendorIdToAction.get(tcfDefinerService.getGdprHostVendorId())
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

        final UidsCookieUpdateResult uidsCookieUpdateResult =
                uidsCookieService.updateUidsCookie(setuidContext.getUidsCookie(), bidder, uid);
        final Cookie updatedUidsCookie = uidsCookieService.toCookie(uidsCookieUpdateResult.getUidsCookie());
        addCookie(routingContext, updatedUidsCookie);

        if (uidsCookieUpdateResult.isSuccessfullyUpdated()) {
            metrics.updateUserSyncSetsMetric(bidder);
        }
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

    private boolean shouldRespondWithPixel(String format, UsersyncMethodType syncType) {
        return UsersyncFormat.PIXEL.name.equals(format)
                || (!UsersyncFormat.BLANK.name.equals(format) && syncType == UsersyncMethodType.REDIRECT);
    }

    private void handleErrors(Throwable error, RoutingContext routingContext, TcfContext tcfContext) {
        final String message = error.getMessage();
        final HttpResponseStatus status;
        final String body;
        if (error instanceof InvalidRequestException) {
            metrics.updateUserSyncBadRequestMetric();
            status = HttpResponseStatus.BAD_REQUEST;
            body = "Invalid request format: " + message;
        } else if (error instanceof UnauthorizedUidsException) {
            metrics.updateUserSyncOptoutMetric();
            status = HttpResponseStatus.UNAUTHORIZED;
            body = "Unauthorized: " + message;
        } else if (error instanceof UnavailableForLegalReasonsException) {
            status = HttpResponseStatus.valueOf(451);
            body = "Unavailable For Legal Reasons.";
        } else if (error instanceof InvalidAccountConfigException) {
            metrics.updateUserSyncBadRequestMetric();
            status = HttpResponseStatus.BAD_REQUEST;
            body = "Invalid account configuration: " + message;
        } else {
            status = HttpResponseStatus.INTERNAL_SERVER_ERROR;
            body = "Unexpected setuid processing error: " + message;
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
        routingContext.response().headers().add(HttpUtil.SET_COOKIE_HEADER, cookie.encode());
    }
}
