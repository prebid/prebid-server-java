package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import lombok.Value;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.analytics.model.CookieSyncEvent;
import org.prebid.server.analytics.reporter.AnalyticsReporterDelegator;
import org.prebid.server.auction.PrivacyEnforcementService;
import org.prebid.server.auction.model.CookieSyncContext;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.UsersyncInfoBuilder;
import org.prebid.server.bidder.UsersyncMethod;
import org.prebid.server.bidder.UsersyncMethodChooser;
import org.prebid.server.bidder.UsersyncUtil;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.cookie.model.UidWithExpiry;
import org.prebid.server.cookie.proto.Uids;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.exception.UnauthorizedUidsException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.metric.Metrics;
import org.prebid.server.model.Endpoint;
import org.prebid.server.privacy.gdpr.TcfDefinerService;
import org.prebid.server.privacy.gdpr.model.HostVendorTcfResponse;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.gdpr.model.TcfResponse;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.proto.request.CookieSyncRequest;
import org.prebid.server.proto.response.BidderUsersyncStatus;
import org.prebid.server.proto.response.CookieSyncResponse;
import org.prebid.server.proto.response.UsersyncInfo;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountCookieSyncConfig;
import org.prebid.server.settings.model.AccountGdprConfig;
import org.prebid.server.settings.model.AccountPrivacyConfig;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class CookieSyncHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(CookieSyncHandler.class);
    private static final ConditionalLogger BAD_REQUEST_LOGGER = new ConditionalLogger(logger);

    private static final String REJECTED_BY_TCF = "Rejected by TCF";
    private static final String REJECTED_BY_CCPA = "Rejected by CCPA";
    private static final String METRICS_UNKNOWN_BIDDER = "UNKNOWN";

    private final String externalUrl;
    private final long defaultTimeout;
    private final Integer coopSyncDefaultLimit;
    private final Integer coopSyncMaxLimit;
    private final UidsCookieService uidsCookieService;
    private final ApplicationSettings applicationSettings;
    private final BidderCatalog bidderCatalog;
    private final Set<String> usersyncReadyBidders;
    private final TcfDefinerService tcfDefinerService;
    private final PrivacyEnforcementService privacyEnforcementService;
    private final Integer gdprHostVendorId;
    private final Boolean defaultCoopSync;
    private final List<Collection<String>> listOfCoopSyncBidders;
    private final Set<String> setOfCoopSyncBidders;
    private final AnalyticsReporterDelegator analyticsDelegator;
    private final Metrics metrics;
    private final TimeoutFactory timeoutFactory;
    private final JacksonMapper mapper;

    public CookieSyncHandler(String externalUrl,
                             long defaultTimeout,
                             Integer coopSyncDefaultLimit,
                             Integer coopSyncMaxLimit,
                             UidsCookieService uidsCookieService,
                             ApplicationSettings applicationSettings,
                             BidderCatalog bidderCatalog,
                             TcfDefinerService tcfDefinerService,
                             PrivacyEnforcementService privacyEnforcementService,
                             Integer gdprHostVendorId,
                             boolean defaultCoopSync,
                             List<Collection<String>> listOfCoopSyncBidders,
                             AnalyticsReporterDelegator analyticsDelegator,
                             Metrics metrics,
                             TimeoutFactory timeoutFactory,
                             JacksonMapper mapper) {

        this.externalUrl = HttpUtil.validateUrl(Objects.requireNonNull(externalUrl));
        this.defaultTimeout = defaultTimeout;
        this.coopSyncDefaultLimit = coopSyncDefaultLimit;
        this.coopSyncMaxLimit = coopSyncMaxLimit;
        this.uidsCookieService = Objects.requireNonNull(uidsCookieService);
        this.applicationSettings = Objects.requireNonNull(applicationSettings);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.usersyncReadyBidders = usersyncReadyBidders(bidderCatalog);
        this.tcfDefinerService = Objects.requireNonNull(tcfDefinerService);
        this.privacyEnforcementService = Objects.requireNonNull(privacyEnforcementService);
        this.gdprHostVendorId = validateHostVendorId(gdprHostVendorId);
        this.defaultCoopSync = defaultCoopSync;
        this.listOfCoopSyncBidders = prepareCoopSyncBidders(listOfCoopSyncBidders, bidderCatalog);
        this.setOfCoopSyncBidders = flatMapToSet(this.listOfCoopSyncBidders);
        this.analyticsDelegator = Objects.requireNonNull(analyticsDelegator);
        this.metrics = Objects.requireNonNull(metrics);
        this.timeoutFactory = Objects.requireNonNull(timeoutFactory);
        this.mapper = Objects.requireNonNull(mapper);
    }

    private static List<Collection<String>> prepareCoopSyncBidders(List<Collection<String>> coopSyncBidders,
                                                                   BidderCatalog bidderCatalog) {

        if (CollectionUtils.isEmpty(coopSyncBidders)) {
            logger.info("Coop-sync bidder list is not provided, will use active bidders with configured user-sync");
            return Collections.singletonList(usersyncReadyBidders(bidderCatalog));
        }

        final List<Collection<String>> validBidderGroups = new ArrayList<>();
        for (Collection<String> coopSyncGroup : coopSyncBidders) {
            final Set<String> validBidderGroup = new HashSet<>();

            for (String bidderName : coopSyncGroup) {
                if (!bidderCatalog.isActive(bidderName)) {
                    logger.info("""
                            bidder {0} is provided for coop-syncing, \
                            but disabled in current pbs instance, ignoring""", bidderName);
                } else if (bidderCatalog.usersyncerByName(bidderName).isEmpty()) {
                    logger.info("""
                            bidder {0} is provided for coop-syncing, \
                            but has no user-sync configuration, ignoring""", bidderName);
                } else {
                    validBidderGroup.add(bidderName);
                }
            }
            validBidderGroups.add(validBidderGroup);
        }

        return validBidderGroups;
    }

    private static Set<String> usersyncReadyBidders(BidderCatalog bidderCatalog) {
        return bidderCatalog.names().stream()
                .filter(bidderCatalog::isActive)
                .filter(bidder -> bidderCatalog.usersyncerByName(bidder).isPresent())
                .collect(Collectors.toSet());
    }

    private static Integer validateHostVendorId(Integer gdprHostVendorId) {
        if (gdprHostVendorId == null) {
            logger.warn("gdpr.host-vendor-id not specified. Will skip host company GDPR checks");
        }
        return gdprHostVendorId;
    }

    private static Set<String> flatMapToSet(List<Collection<String>> listOfStringLists) {
        return listOfStringLists.stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
    }

    @Override
    public void handle(RoutingContext routingContext) {
        metrics.updateCookieSyncRequestMetric();

        toCookieSyncContext(routingContext)
                .onComplete(cookieSyncContextResult ->
                        handleCookieSyncContextResult(cookieSyncContextResult, routingContext));
    }

    private Future<CookieSyncContext> toCookieSyncContext(RoutingContext routingContext) {
        final CookieSyncRequest cookieSyncRequest;
        try {
            cookieSyncRequest = parseRequest(routingContext);
        } catch (Exception e) {
            return Future.failedFuture(e);
        }

        final String requestAccount = cookieSyncRequest.getAccount();
        final Timeout timeout = timeoutFactory.create(defaultTimeout);
        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(routingContext);

        return accountById(requestAccount, timeout)
                .compose(account -> privacyEnforcementService.contextFromCookieSyncRequest(
                                cookieSyncRequest, routingContext.request(), account, timeout)
                        .map(privacyContext -> CookieSyncContext.builder()
                                .routingContext(routingContext)
                                .uidsCookie(uidsCookie)
                                .cookieSyncRequest(cookieSyncRequest)
                                .usersyncMethodChooser(
                                        UsersyncMethodChooser.from(cookieSyncRequest.getFilterSettings()))
                                .timeout(timeout)
                                .account(account)
                                .privacyContext(privacyContext)
                                .build()));
    }

    private CookieSyncRequest parseRequest(RoutingContext routingContext) {
        final Buffer body = routingContext.getBody();
        if (body == null) {
            throw new InvalidRequestException("Request has no body");
        }

        try {
            return mapper.decodeValue(body, CookieSyncRequest.class);
        } catch (DecodeException e) {
            final String message = "Request body cannot be parsed";
            logger.info(message, e);
            throw new InvalidRequestException(message);
        }
    }

    private Future<Account> accountById(String accountId, Timeout timeout) {
        return StringUtils.isBlank(accountId)
                ? Future.succeededFuture(Account.empty(accountId))
                : applicationSettings.getAccountById(accountId, timeout)
                .otherwise(Account.empty(accountId));
    }

    private void handleCookieSyncContextResult(AsyncResult<CookieSyncContext> cookieSyncContextResult,
                                               RoutingContext routingContext) {

        if (cookieSyncContextResult.succeeded()) {
            final CookieSyncContext cookieSyncContext = cookieSyncContextResult.result();

            final TcfContext tcfContext = cookieSyncContext.getPrivacyContext().getTcfContext();
            try {
                validateCookieSyncContext(cookieSyncContext);
            } catch (InvalidRequestException | UnauthorizedUidsException e) {
                handleErrors(e, routingContext, tcfContext);
                return;
            }

            isAllowedForHostVendorId(tcfContext)
                    .onComplete(hostTcfResponseResult -> respondByTcfResponse(
                            hostTcfResponseResult,
                            biddersToSync(cookieSyncContext),
                            cookieSyncContext));
        } else {
            handleErrors(cookieSyncContextResult.cause(), routingContext, null);
        }
    }

    private void validateCookieSyncContext(CookieSyncContext cookieSyncContext) {
        final UidsCookie uidsCookie = cookieSyncContext.getUidsCookie();
        if (!uidsCookie.allowsSync()) {
            throw new UnauthorizedUidsException("Sync is not allowed for this uids");
        }

        final CookieSyncRequest cookieSyncRequest = cookieSyncContext.getCookieSyncRequest();
        if (isGdprParamsNotConsistent(cookieSyncRequest)) {
            throw new InvalidRequestException("gdpr_consent is required if gdpr is 1");
        }

        final TcfContext tcfContext = cookieSyncContext.getPrivacyContext().getTcfContext();
        if (tcfContext.isInGdprScope() && !tcfContext.isConsentValid()) {
            metrics.updateUserSyncTcfInvalidMetric();
            throw new InvalidRequestException("Consent string is invalid");
        }
    }

    private static boolean isGdprParamsNotConsistent(CookieSyncRequest request) {
        return Objects.equals(request.getGdpr(), 1) && StringUtils.isBlank(request.getGdprConsent());
    }

    /**
     * Returns bidder names to sync.
     * <p>
     * If bidder list was omitted in request, that means sync should be done for all bidders.
     */
    private Set<String> biddersToSync(CookieSyncContext cookieSyncContext) {
        final CookieSyncRequest cookieSyncRequest = cookieSyncContext.getCookieSyncRequest();

        final List<String> requestBidders = cookieSyncRequest.getBidders();

        if (CollectionUtils.isEmpty(requestBidders)) {
            return usersyncReadyBidders;
        }

        final Account account = cookieSyncContext.getAccount();

        if (coopSyncAllowed(cookieSyncRequest, account)) {
            final Integer requestLimit = resolveLimit(cookieSyncContext);

            return requestLimit == null
                    ? addAllCoopSyncBidders(requestBidders)
                    : addCoopSyncBidders(requestBidders, requestLimit);
        }

        return new HashSet<>(requestBidders);
    }

    private Boolean coopSyncAllowed(CookieSyncRequest cookieSyncRequest, Account account) {
        final Boolean requestCoopSync = cookieSyncRequest.getCoopSync();
        if (requestCoopSync != null) {
            return requestCoopSync;
        }

        final AccountCookieSyncConfig accountCookieSyncConfig = account.getCookieSync();
        final Boolean accountCoopSync = accountCookieSyncConfig != null
                ? accountCookieSyncConfig.getDefaultCoopSync()
                : null;

        return ObjectUtils.firstNonNull(accountCoopSync, defaultCoopSync);
    }

    /**
     * If host vendor id is null, host allowed to sync cookies.
     */
    private Future<HostVendorTcfResponse> isAllowedForHostVendorId(TcfContext tcfContext) {
        return gdprHostVendorId == null
                ? Future.succeededFuture(HostVendorTcfResponse.allowedVendor())
                : tcfDefinerService.resultForVendorIds(Collections.singleton(gdprHostVendorId), tcfContext)
                .map(this::toHostVendorTcfResponse);
    }

    private HostVendorTcfResponse toHostVendorTcfResponse(TcfResponse<Integer> tcfResponse) {
        return HostVendorTcfResponse.of(tcfResponse.getUserInGdprScope(), tcfResponse.getCountry(),
                isCookieSyncAllowed(tcfResponse));
    }

    private boolean isCookieSyncAllowed(TcfResponse<Integer> hostTcfResponse) {
        final Map<Integer, PrivacyEnforcementAction> vendorIdToAction = hostTcfResponse.getActions();
        final PrivacyEnforcementAction hostActions = vendorIdToAction != null
                ? vendorIdToAction.get(gdprHostVendorId)
                : null;

        return hostActions != null && !hostActions.isBlockPixelSync();
    }

    private Set<String> addAllCoopSyncBidders(List<String> bidders) {
        final Set<String> updatedBidders = new HashSet<>(setOfCoopSyncBidders);
        updatedBidders.addAll(bidders);
        return updatedBidders;
    }

    private Set<String> addCoopSyncBidders(List<String> bidders, int limit) {
        if (limit <= 0) {
            return new HashSet<>(bidders);
        }
        final Set<String> allBidders = new HashSet<>(bidders);

        for (Collection<String> prioritisedBidders : listOfCoopSyncBidders) {
            int remaining = limit - allBidders.size();
            if (remaining <= 0) {
                return allBidders;
            }

            if (prioritisedBidders.size() > remaining) {
                final List<String> list = new ArrayList<>(prioritisedBidders);
                Collections.shuffle(list);
                for (String prioritisedBidder : list) {
                    if (allBidders.add(prioritisedBidder)) {
                        if (allBidders.size() >= limit) {
                            break;
                        }
                    }
                }
            } else {
                allBidders.addAll(prioritisedBidders);
            }
        }
        return allBidders;
    }

    private void respondByTcfResponse(AsyncResult<HostVendorTcfResponse> hostTcfResponseResult,
                                      Set<String> biddersToSync,
                                      CookieSyncContext cookieSyncContext) {

        final TcfContext tcfContext = cookieSyncContext.getPrivacyContext().getTcfContext();
        if (hostTcfResponseResult.succeeded()) {

            // Host vendor tcf response can be not populated if host vendor id is not defined,
            // we can't be sure if we can use it. So we get tcf values from response for all bidders.
            final HostVendorTcfResponse hostVendorTcfResponse = hostTcfResponseResult.result();
            if (hostVendorTcfResponse.isVendorAllowed()) {

                final AccountPrivacyConfig accountPrivacyConfig = cookieSyncContext.getAccount().getPrivacy();
                final AccountGdprConfig accountGdprConfig =
                        accountPrivacyConfig != null ? accountPrivacyConfig.getGdpr() : null;
                tcfDefinerService.resultForBidderNames(biddersToSync, tcfContext, accountGdprConfig)
                        .onComplete(tcfResponseResult -> respondByTcfResultForBidders(tcfResponseResult,
                                biddersToSync, cookieSyncContext));
            } else {
                // Reject all bidders when Host TCF response has blocked pixel
                final RejectedBidders rejectedBidders = RejectedBidders.of(biddersToSync, Collections.emptySet());
                respondWithRejectedBidders(cookieSyncContext, biddersToSync, rejectedBidders);
            }

        } else {
            final Throwable error = hostTcfResponseResult.cause();
            final RoutingContext routingContext = cookieSyncContext.getRoutingContext();
            handleErrors(error, routingContext, tcfContext);
        }
    }

    private void respondByTcfResultForBidders(AsyncResult<TcfResponse<String>> tcfResponseResult,
                                              Set<String> biddersToSync,
                                              CookieSyncContext cookieSyncContext) {
        if (tcfResponseResult.succeeded()) {
            final TcfResponse<String> tcfResponse = tcfResponseResult.result();

            final RejectedBidders rejectedBidders = rejectedRequestBiddersToSync(tcfResponse, cookieSyncContext,
                    biddersToSync);

            respondWithRejectedBidders(cookieSyncContext, biddersToSync, rejectedBidders);
        } else {
            final Throwable error = tcfResponseResult.cause();
            final RoutingContext routingContext = cookieSyncContext.getRoutingContext();
            final TcfContext tcfContext = cookieSyncContext.getPrivacyContext().getTcfContext();
            handleErrors(error, routingContext, tcfContext);
        }
    }

    private RejectedBidders rejectedRequestBiddersToSync(TcfResponse<String> tcfResponse,
                                                         CookieSyncContext cookieSyncContext,
                                                         Set<String> biddersToSync) {

        final Account account = cookieSyncContext.getAccount();
        final Privacy privacy = cookieSyncContext.getPrivacyContext().getPrivacy();
        final Set<String> ccpaEnforcedBidders = extractCcpaEnforcedBidders(account, biddersToSync, privacy);

        final Map<String, PrivacyEnforcementAction> bidderNameToAction = tcfResponse.getActions();

        final Set<String> biddersRejectedByTcf = biddersToSync.stream()
                .filter(bidder -> !ccpaEnforcedBidders.contains(bidder))
                .filter(bidder ->
                        !bidderNameToAction.containsKey(bidder)
                                || bidderNameToAction.get(bidder).isBlockPixelSync())
                .collect(Collectors.toSet());

        return RejectedBidders.of(biddersRejectedByTcf, ccpaEnforcedBidders);
    }

    private Set<String> extractCcpaEnforcedBidders(Account account, Collection<String> biddersToSync, Privacy privacy) {
        if (privacyEnforcementService.isCcpaEnforced(privacy.getCcpa(), account)) {
            return biddersToSync.stream()
                    .filter(bidder -> bidderCatalog.bidderInfoByName(bidder).isCcpaEnforced())
                    .collect(Collectors.toSet());
        }
        return Collections.emptySet();
    }

    /**
     * Make HTTP response for given bidders.
     */
    private void respondWithRejectedBidders(CookieSyncContext cookieSyncContext,
                                            Collection<String> bidders,
                                            RejectedBidders rejectedBidders) {

        updateCookieSyncTcfMetrics(bidders, rejectedBidders.getRejectedByTcf());

        final UidsCookie uidsCookie = cookieSyncContext.getUidsCookie();
        final List<BidderUsersyncStatus> bidderStatuses = bidders.stream()
                .map(bidder -> bidderStatusFor(bidder, cookieSyncContext, rejectedBidders))
                .filter(Objects::nonNull) // skip bidder with live UID
                .toList();

        updateCookieSyncMatchMetrics(bidders, bidderStatuses);

        final List<BidderUsersyncStatus> trimmedBidderStatuses =
                trimBiddersToLimit(bidderStatuses, resolveLimit(cookieSyncContext));

        final String cookieSyncStatus = uidsCookie.hasLiveUids() ? "ok" : "no_cookie";

        final HttpResponseStatus status = HttpResponseStatus.OK;
        final CookieSyncResponse cookieSyncResponse = CookieSyncResponse.of(cookieSyncStatus, trimmedBidderStatuses);
        final String body = mapper.encodeToString(cookieSyncResponse);

        HttpUtil.executeSafely(cookieSyncContext.getRoutingContext(), Endpoint.cookie_sync,
                response -> response
                        .setStatusCode(status.code())
                        .putHeader(HttpUtil.CONTENT_TYPE_HEADER, HttpHeaderValues.APPLICATION_JSON)
                        .end(body));

        final CookieSyncEvent event = CookieSyncEvent.builder()
                .status(status.code())
                .bidderStatus(trimmedBidderStatuses)
                .build();
        final TcfContext tcfContext = cookieSyncContext.getPrivacyContext().getTcfContext();
        analyticsDelegator.processEvent(event, tcfContext);
    }

    private void updateCookieSyncTcfMetrics(Collection<String> syncBidders, Collection<String> rejectedBidders) {
        for (String bidder : syncBidders) {
            if (rejectedBidders.contains(bidder)) {
                metrics.updateCookieSyncTcfBlockedMetric(
                        bidderCatalog.isValidName(bidder) ? bidder : METRICS_UNKNOWN_BIDDER);
            } else {
                metrics.updateCookieSyncGenMetric(bidder);
            }
        }
    }

    /**
     * Creates {@link BidderUsersyncStatus} for given bidder.
     */
    private BidderUsersyncStatus bidderStatusFor(String bidder,
                                                 CookieSyncContext cookieSyncContext,
                                                 RejectedBidders rejectedBidders) {

        final Set<String> biddersRejectedByTcf = rejectedBidders.getRejectedByTcf();
        final Set<String> biddersRejectedByCcpa = rejectedBidders.getRejectedByCcpa();

        if (!bidderCatalog.isValidName(bidder)) {
            return bidderStatusBuilder(bidder)
                    .error("Unsupported bidder")
                    .build();
        } else if (!bidderCatalog.isActive(bidder)) {
            return bidderStatusBuilder(bidder)
                    .error(bidder + """
                             is not configured properly on this Prebid Server deploy. \
                            If you believe this should work, contact the company hosting \
                            the service and tell them to check their configuration.""")
                    .build();
        } else if (biddersRejectedByTcf.contains(bidder)) {
            return bidderStatusBuilder(bidder)
                    .error(REJECTED_BY_TCF)
                    .build();
        } else if (biddersRejectedByCcpa.contains(bidder)) {
            return bidderStatusBuilder(bidder)
                    .error(REJECTED_BY_CCPA)
                    .build();
        }

        final Optional<Usersyncer> usersyncer = bidderCatalog.usersyncerByName(bidder);
        final UsersyncMethod usersyncMethod = usersyncer
                .map(syncer -> cookieSyncContext.getUsersyncMethodChooser().choose(syncer, bidder))
                .orElse(null);

        if (usersyncMethod == null) {
            return bidderStatusBuilder(bidder)
                    .error(bidder + " is requested for syncing, but doesn't have appropriate sync method")
                    .build();
        }

        final RoutingContext routingContext = cookieSyncContext.getRoutingContext();
        final UidsCookie uidsCookie = cookieSyncContext.getUidsCookie();
        final String cookieFamilyName = usersyncer.get().getCookieFamilyName();
        final String uidFromHostCookieToSet = resolveUidFromHostCookie(routingContext, cookieFamilyName);
        if (uidFromHostCookieToSet == null && uidsCookie.hasLiveUidFrom(cookieFamilyName)) {
            return null;
        }

        final Privacy privacy = cookieSyncContext.getPrivacyContext().getPrivacy();

        return bidderStatusBuilder(bidder)
                .noCookie(true)
                .usersync(toUsersyncInfo(usersyncMethod, cookieFamilyName, uidFromHostCookieToSet, privacy))
                .build();
    }

    private static BidderUsersyncStatus.BidderUsersyncStatusBuilder bidderStatusBuilder(String bidder) {
        return BidderUsersyncStatus.builder().bidder(bidder);
    }

    /**
     * Returns UID from host cookie to sync with uids cookie or null if normal usersync flow should be applied.
     * <p>
     * Uids cookie should be in sync with host-cookie value, so the next conditions must be satisfied:
     * <p>
     * 1. Given {@link Usersyncer} should have the same cookie family value as configured host-cookie-family.
     * <p>
     * 2. Host-cookie must be present in HTTP request.
     * <p>
     * 3. Host-bidder uid value in uids cookie should not exist or be different from host-cookie uid value.
     */
    private String resolveUidFromHostCookie(RoutingContext routingContext, String cookieFamilyName) {
        if (!Objects.equals(cookieFamilyName, uidsCookieService.getHostCookieFamily())) {
            return null;
        }

        final Map<String, String> cookies = HttpUtil.cookiesAsMap(routingContext);
        final String hostCookieUid = uidsCookieService.parseHostCookie(cookies);

        if (hostCookieUid == null) {
            return null;
        }

        final Uids parsedUids = uidsCookieService.parseUids(cookies);
        final Map<String, UidWithExpiry> uidsMap = parsedUids != null ? parsedUids.getUids() : null;
        final UidWithExpiry uidWithExpiry = uidsMap != null ? uidsMap.get(cookieFamilyName) : null;
        final String uid = uidWithExpiry != null ? uidWithExpiry.getUid() : null;

        if (Objects.equals(hostCookieUid, uid)) {
            return null;
        }

        return hostCookieUid;
    }

    private UsersyncInfo toUsersyncInfo(UsersyncMethod usersyncMethod,
                                        String cookieFamilyName,
                                        String uidFromHostCookieToSet,
                                        Privacy privacy) {

        final UsersyncInfoBuilder usersyncInfoBuilder = UsersyncInfoBuilder.from(usersyncMethod);
        if (uidFromHostCookieToSet != null) {
            usersyncInfoBuilder
                    .usersyncUrl(toHostBidderUsersyncUrl(cookieFamilyName, usersyncMethod, uidFromHostCookieToSet))
                    .redirectUrl(null);
        }

        return usersyncInfoBuilder
                .privacy(privacy)
                .build();
    }

    /**
     * Returns updated usersync-url pointed directly to Prebid Server /setuid endpoint.
     */
    private String toHostBidderUsersyncUrl(String cookieFamilyName,
                                           UsersyncMethod usersyncMethod,
                                           String hostCookieUid) {

        final String url = UsersyncUtil.CALLBACK_URL_TEMPLATE.formatted(
                externalUrl,
                cookieFamilyName,
                HttpUtil.encodeUrl(hostCookieUid));

        return UsersyncUtil.enrichUrlWithFormat(url, UsersyncUtil.resolveFormat(usersyncMethod));
    }

    private void updateCookieSyncMatchMetrics(Collection<String> syncBidders,
                                              Collection<BidderUsersyncStatus> requiredUsersyncs) {
        syncBidders.stream()
                .filter(bidder -> requiredUsersyncs.stream().noneMatch(usersync -> bidder.equals(usersync.getBidder())))
                .forEach(metrics::updateCookieSyncMatchesMetric);
    }

    private Integer resolveLimit(CookieSyncContext cookieSyncContext) {
        final AccountCookieSyncConfig accountCookieSyncConfig = cookieSyncContext.getAccount().getCookieSync();

        final Integer resolvedLimit = ObjectUtils.firstNonNull(
                cookieSyncContext.getCookieSyncRequest().getLimit(),
                ObjectUtil.getIfNotNull(accountCookieSyncConfig, AccountCookieSyncConfig::getDefaultLimit),
                coopSyncDefaultLimit);

        final Integer resolvedMaxLimit = ObjectUtils.firstNonNull(
                ObjectUtil.getIfNotNull(accountCookieSyncConfig, AccountCookieSyncConfig::getMaxLimit),
                coopSyncMaxLimit);

        return resolvedLimit != null && resolvedMaxLimit != null && resolvedLimit > resolvedMaxLimit
                ? resolvedMaxLimit
                : resolvedLimit;
    }

    private static List<BidderUsersyncStatus> trimBiddersToLimit(List<BidderUsersyncStatus> bidderStatuses,
                                                                 Integer limit) {

        if (limit == null || limit <= 0 || limit >= bidderStatuses.size()) {
            return bidderStatuses;
        }

        final List<BidderUsersyncStatus> allowedStatuses = bidderStatuses.stream()
                .filter(status -> StringUtils.isEmpty(status.getError()))
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(allowedStatuses);

        final List<BidderUsersyncStatus> rejectedStatuses = bidderStatuses.stream()
                .filter(status -> StringUtils.isNotEmpty(status.getError()))
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(rejectedStatuses);

        return ListUtils.union(allowedStatuses, rejectedStatuses).subList(0, limit);
    }

    private void handleErrors(Throwable error, RoutingContext routingContext, TcfContext tcfContext) {
        final String message = error.getMessage();
        final HttpResponseStatus status;
        final String body;

        if (error instanceof InvalidRequestException) {
            status = HttpResponseStatus.BAD_REQUEST;
            body = "Invalid request format: " + message;

            metrics.updateUserSyncBadRequestMetric();
            BAD_REQUEST_LOGGER.info(message, 0.01);
        } else if (error instanceof UnauthorizedUidsException) {
            status = HttpResponseStatus.UNAUTHORIZED;
            body = "Unauthorized: " + message;

            metrics.updateUserSyncOptoutMetric();
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

    @Value(staticConstructor = "of")
    private static class RejectedBidders {

        Set<String> rejectedByTcf;

        Set<String> rejectedByCcpa;
    }
}
