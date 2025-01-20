package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
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
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.execution.timeout.TimeoutFactory;
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
import org.prebid.server.settings.model.AccountGdprConfig;
import org.prebid.server.settings.model.AccountPrivacyConfig;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.StreamUtil;
import org.prebid.server.vertx.verticles.server.HttpEndpoint;
import org.prebid.server.vertx.verticles.server.application.ApplicationResource;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
    private final Map<String, Pair<String, UsersyncMethodType>> cookieNameToBidderAndSyncType;

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
        this.cookieNameToBidderAndSyncType = collectUsersyncers(bidderCatalog);
    }

    private static Map<String, Pair<String, UsersyncMethodType>> collectUsersyncers(BidderCatalog bidderCatalog) {
        validateUsersyncersDuplicates(bidderCatalog);

        return bidderCatalog.usersyncReadyBidders().stream()
                .sorted(Comparator.comparing(bidderName -> BooleanUtils.toInteger(bidderCatalog.isAlias(bidderName))))
                .filter(StreamUtil.distinctBy(bidderCatalog::cookieFamilyName))
                .map(bidderName -> bidderCatalog.usersyncerByName(bidderName)
                        .map(usersyncer -> Pair.of(bidderName, usersyncer)))
                .flatMap(Optional::stream)
                .collect(Collectors.toMap(
                        pair -> pair.getRight().getCookieFamilyName(),
                        pair -> Pair.of(pair.getLeft(), preferredUserSyncType(pair.getRight()))));
    }

    private static void validateUsersyncersDuplicates(BidderCatalog bidderCatalog) {
        final List<String> duplicatedCookieFamilyNames = bidderCatalog.usersyncReadyBidders().stream()
                .filter(bidderName -> !isAliasWithRootCookieFamilyName(bidderCatalog, bidderName))
                .map(bidderCatalog::usersyncerByName)
                .flatMap(Optional::stream)
                .map(Usersyncer::getCookieFamilyName)
                .filter(Predicate.not(StreamUtil.distinctBy(Function.identity())))
                .distinct()
                .toList();

        if (!duplicatedCookieFamilyNames.isEmpty()) {
            throw new IllegalArgumentException(
                    "Duplicated \"cookie-family-name\" found, values: "
                            + String.join(", ", duplicatedCookieFamilyNames));
        }
    }

    private static boolean isAliasWithRootCookieFamilyName(BidderCatalog bidderCatalog, String bidder) {
        final String bidderCookieFamilyName = bidderCatalog.cookieFamilyName(bidder).orElse(StringUtils.EMPTY);
        final String parentCookieFamilyName =
                bidderCatalog.cookieFamilyName(bidderCatalog.resolveBaseBidder(bidder)).orElse(null);

        return bidderCatalog.isAlias(bidder)
                && parentCookieFamilyName != null
                && parentCookieFamilyName.equals(bidderCookieFamilyName);
    }

    private static UsersyncMethodType preferredUserSyncType(Usersyncer usersyncer) {
        return ObjectUtils.firstNonNull(usersyncer.getIframe(), usersyncer.getRedirect()).getType();
    }

    @Override
    public List<HttpEndpoint> endpoints() {
        return Collections.singletonList(HttpEndpoint.of(HttpMethod.GET, Endpoint.setuid.value()));
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

        final UsersyncMethodType syncType = Optional.ofNullable(cookieName)
                .map(cookieNameToBidderAndSyncType::get)
                .map(Pair::getRight)
                .orElse(null);

        return accountById(requestAccount, timeout)
                .compose(account -> setuidPrivacyContextFactory.contextFrom(httpRequest, account, timeout)
                        .map(privacyContext -> SetuidContext.builder()
                                .routingContext(routingContext)
                                .uidsCookie(uidsCookie)
                                .timeout(timeout)
                                .account(account)
                                .cookieName(cookieName)
                                .syncType(syncType)
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
            final String bidderCookieFamily = setuidContext.getCookieName();
            final TcfContext tcfContext = setuidContext.getPrivacyContext().getTcfContext();

            try {
                validateSetuidContext(setuidContext, bidderCookieFamily);
            } catch (InvalidRequestException | UnauthorizedUidsException | UnavailableForLegalReasonsException e) {
                handleErrors(e, routingContext, tcfContext);
                return;
            }

            final AccountPrivacyConfig privacyConfig = setuidContext.getAccount().getPrivacy();
            final AccountGdprConfig accountGdprConfig = privacyConfig != null ? privacyConfig.getGdpr() : null;

            final String bidderName = cookieNameToBidderAndSyncType.get(bidderCookieFamily).getLeft();

            Future.all(
                            tcfDefinerService.isAllowedForHostVendorId(tcfContext),
                            tcfDefinerService.resultForBidderNames(
                                    Collections.singleton(bidderName), tcfContext, accountGdprConfig))
                    .onComplete(hostTcfResponseResult -> respondByTcfResponse(
                            hostTcfResponseResult,
                            bidderName,
                            setuidContext));
        } else {
            final Throwable error = setuidContextResult.cause();
            handleErrors(error, routingContext, null);
        }
    }

    private void validateSetuidContext(SetuidContext setuidContext, String bidderCookieFamily) {
        final String cookieName = setuidContext.getCookieName();
        final boolean isCookieNameBlank = StringUtils.isBlank(cookieName);
        if (isCookieNameBlank || !cookieNameToBidderAndSyncType.containsKey(cookieName)) {
            final String cookieNameError = isCookieNameBlank ? "required" : "invalid";
            throw new InvalidRequestException("\"bidder\" query param is " + cookieNameError);
        }

        final TcfContext tcfContext = setuidContext.getPrivacyContext().getTcfContext();
        if (tcfContext.isInGdprScope() && !tcfContext.isConsentValid()) {
            metrics.updateUserSyncTcfInvalidMetric(bidderCookieFamily);
            throw new InvalidRequestException("Consent string is invalid");
        }

        final UidsCookie uidsCookie = setuidContext.getUidsCookie();
        if (!uidsCookie.allowsSync()) {
            throw new UnauthorizedUidsException("Sync is not allowed for this uids", tcfContext);
        }

        final ActivityInfrastructure activityInfrastructure = setuidContext.getActivityInfrastructure();
        final ActivityInvocationPayload activityInvocationPayload = TcfContextActivityInvocationPayload.of(
                ActivityInvocationPayloadImpl.of(ComponentType.BIDDER, bidderCookieFamily),
                tcfContext);

        if (!activityInfrastructure.isAllowed(Activity.SYNC_USER, activityInvocationPayload)) {
            throw new UnavailableForLegalReasonsException();
        }
    }

    private void respondByTcfResponse(AsyncResult<CompositeFuture> hostTcfResponseResult,
                                      String bidderName,
                                      SetuidContext setuidContext) {

        final TcfContext tcfContext = setuidContext.getPrivacyContext().getTcfContext();
        final RoutingContext routingContext = setuidContext.getRoutingContext();

        if (hostTcfResponseResult.succeeded()) {
            final CompositeFuture compositeFuture = hostTcfResponseResult.result();
            final HostVendorTcfResponse hostVendorTcfResponse = compositeFuture.resultAt(0);
            final TcfResponse<String> bidderTcfResponse = compositeFuture.resultAt(1);

            final Map<String, PrivacyEnforcementAction> vendorIdToAction = bidderTcfResponse.getActions();
            final PrivacyEnforcementAction action = vendorIdToAction != null
                    ? vendorIdToAction.get(bidderName)
                    : null;

            final boolean notInGdprScope = BooleanUtils.isFalse(bidderTcfResponse.getUserInGdprScope());
            final boolean isBidderVendorAllowed = notInGdprScope || action == null || !action.isBlockPixelSync();

            if (hostVendorTcfResponse.isVendorAllowed() && isBidderVendorAllowed) {
                respondWithCookie(setuidContext);
            } else {
                metrics.updateUserSyncTcfBlockedMetric(setuidContext.getCookieName());

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
            metrics.updateUserSyncTcfBlockedMetric(setuidContext.getCookieName());
            handleErrors(error, routingContext, tcfContext);
        }
    }

    private void respondWithCookie(SetuidContext setuidContext) {
        final RoutingContext routingContext = setuidContext.getRoutingContext();
        final String uid = routingContext.request().getParam(UID_PARAM);
        final String bidder = setuidContext.getCookieName();

        final UidsCookieUpdateResult uidsCookieUpdateResult = uidsCookieService.updateUidsCookie(
                setuidContext.getUidsCookie(), bidder, uid);

        uidsCookieUpdateResult.getUidsCookies().entrySet().stream()
                .map(e -> uidsCookieService.toCookie(e.getKey(), e.getValue()))
                .forEach(uidsCookie -> addCookie(routingContext, uidsCookie));

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
        switch (error) {
            case InvalidRequestException invalidRequestException -> {
                metrics.updateUserSyncBadRequestMetric();
                status = HttpResponseStatus.BAD_REQUEST;
                body = "Invalid request format: " + message;
            }
            case UnauthorizedUidsException unauthorizedUidsException -> {
                metrics.updateUserSyncOptoutMetric();
                status = HttpResponseStatus.UNAUTHORIZED;
                body = "Unauthorized: " + message;
            }
            case UnavailableForLegalReasonsException unavailableForLegalReasonsException -> {
                status = HttpResponseStatus.valueOf(451);
                body = "Unavailable For Legal Reasons.";
            }
            case InvalidAccountConfigException invalidAccountConfigException -> {
                metrics.updateUserSyncBadRequestMetric();
                status = HttpResponseStatus.BAD_REQUEST;
                body = "Invalid account configuration: " + message;
            }
            default -> {
                status = HttpResponseStatus.INTERNAL_SERVER_ERROR;
                body = "Unexpected setuid processing error: " + message;
                logger.warn(body, error);
            }
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
