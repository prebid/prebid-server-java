package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.AsciiString;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import lombok.Value;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.analytics.AnalyticsReporterDelegator;
import org.prebid.server.analytics.model.CookieSyncEvent;
import org.prebid.server.auction.PrivacyEnforcementService;
import org.prebid.server.auction.model.CookieSyncContext;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.UsersyncInfoAssembler;
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
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.gdpr.TcfDefinerService;
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
import org.prebid.server.settings.model.AccountGdprConfig;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class CookieSyncHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(CookieSyncHandler.class);
    private static final Map<CharSequence, AsciiString> JSON_HEADERS_MAP = Collections.singletonMap(
            HttpUtil.CONTENT_TYPE_HEADER, HttpHeaderValues.APPLICATION_JSON);

    private static final String REJECTED_BY_TCF = "Rejected by TCF";
    private static final String REJECTED_BY_CCPA = "Rejected by CCPA";

    private final String externalUrl;
    private final long defaultTimeout;
    private final UidsCookieService uidsCookieService;
    private final ApplicationSettings applicationSettings;
    private final BidderCatalog bidderCatalog;
    private final Set<String> activeBidders;
    private final TcfDefinerService tcfDefinerService;
    private final PrivacyEnforcementService privacyEnforcementService;
    private final Integer gdprHostVendorId;
    private final boolean defaultCoopSync;
    private final List<Collection<String>> listOfCoopSyncBidders;
    private final Set<String> setOfCoopSyncBidders;
    private final AnalyticsReporterDelegator analyticsDelegator;
    private final Metrics metrics;
    private final TimeoutFactory timeoutFactory;
    private final JacksonMapper mapper;

    public CookieSyncHandler(String externalUrl,
                             long defaultTimeout,
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
        this.uidsCookieService = Objects.requireNonNull(uidsCookieService);
        this.applicationSettings = Objects.requireNonNull(applicationSettings);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.activeBidders = activeBidders(bidderCatalog);
        this.tcfDefinerService = Objects.requireNonNull(tcfDefinerService);
        this.privacyEnforcementService = Objects.requireNonNull(privacyEnforcementService);
        this.gdprHostVendorId = validateHostVendorId(gdprHostVendorId);
        this.defaultCoopSync = defaultCoopSync;
        this.listOfCoopSyncBidders = CollectionUtils.isNotEmpty(listOfCoopSyncBidders)
                ? listOfCoopSyncBidders
                : Collections.singletonList(activeBidders);
        this.setOfCoopSyncBidders = flatMapToSet(this.listOfCoopSyncBidders);
        this.analyticsDelegator = Objects.requireNonNull(analyticsDelegator);
        this.metrics = Objects.requireNonNull(metrics);
        this.timeoutFactory = Objects.requireNonNull(timeoutFactory);
        this.mapper = Objects.requireNonNull(mapper);
    }

    private static Set<String> activeBidders(BidderCatalog bidderCatalog) {
        return bidderCatalog.names().stream().filter(bidderCatalog::isActive).collect(Collectors.toSet());
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
                .setHandler(cookieSyncContextResult -> handleCookieSyncContextResult(cookieSyncContextResult,
                        routingContext));
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
            final Exception validationException = validateCookieSyncContext(cookieSyncContext);
            if (validationException != null) {
                handleErrors(validationException, routingContext, tcfContext);
                return;
            }

            final CookieSyncRequest cookieSyncRequest = cookieSyncContext.getCookieSyncRequest();
            final Set<String> biddersToSync = biddersToSync(cookieSyncRequest);

            allowedForHostVendorId(tcfContext)
                    .compose(isCookieSyncAllowed ->
                            prepareRejectedBidders(isCookieSyncAllowed, biddersToSync, cookieSyncContext))
                    .setHandler(rejectedBiddersResult ->
                            respondByRejectedBidder(rejectedBiddersResult, biddersToSync, cookieSyncContext));

        } else {
            final Throwable error = cookieSyncContextResult.cause();
            handleErrors(error, routingContext, null);
        }
    }

    private static Exception validateCookieSyncContext(CookieSyncContext cookieSyncContext) {
        final UidsCookie uidsCookie = cookieSyncContext.getUidsCookie();
        if (!uidsCookie.allowsSync()) {
            return new UnauthorizedUidsException("Sync is not allowed for this uids");
        }

        final CookieSyncRequest cookieSyncRequest = cookieSyncContext.getCookieSyncRequest();
        if (isGdprParamsNotConsistent(cookieSyncRequest)) {
            return new InvalidRequestException("gdpr_consent is required if gdpr is 1");
        }

        return null;
    }

    private static boolean isGdprParamsNotConsistent(CookieSyncRequest request) {
        return Objects.equals(request.getGdpr(), 1) && StringUtils.isBlank(request.getGdprConsent());
    }

    /**
     * Returns bidder names to sync.
     * <p>
     * If bidder list was omitted in request, that means sync should be done for all bidders.
     */
    private Set<String> biddersToSync(CookieSyncRequest cookieSyncRequest) {
        final List<String> requestBidders = cookieSyncRequest.getBidders();
        if (CollectionUtils.isEmpty(requestBidders)) {
            return activeBidders;
        }

        final Boolean requestCoopSync = cookieSyncRequest.getCoopSync();
        final boolean coop = requestCoopSync != null ? requestCoopSync : defaultCoopSync;

        if (coop) {
            final Integer limit = cookieSyncRequest.getLimit();
            return limit == null
                    ? addAllCoopSyncBidders(requestBidders)
                    : addCoopSyncBidders(requestBidders, limit);
        }

        return new HashSet<>(requestBidders);
    }

    /**
     * If host vendor id is null, host allowed to sync cookies.
     */
    private Future<Boolean> allowedForHostVendorId(TcfContext tcfContext) {
        return gdprHostVendorId == null
                ? Future.succeededFuture(true)
                : tcfDefinerService.resultForVendorIds(vendorIds, tcfContext)
                .map(this::isCookieSyncAllowed);
    }

    private Boolean isCookieSyncAllowed(TcfResponse<Integer> hostTcfResponse) {
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

    /**
     * Determines original bidder's name.
     */
    private String bidderNameFor(String bidder) {
        return bidderCatalog.isAlias(bidder) ? bidderCatalog.nameByAlias(bidder) : bidder;
    }

    private Future<RejectedBidders> prepareRejectedBidders(Boolean isCookieSyncAllowed,
                                                           Set<String> biddersToSync,
                                                           CookieSyncContext cookieSyncContext) {
        if (BooleanUtils.isTrue(isCookieSyncAllowed)) {

            final TcfContext tcfContext = cookieSyncContext.getPrivacyContext().getTcfContext();
            final AccountGdprConfig accountGdprConfig = cookieSyncContext.getAccount().getGdpr();

            return tcfDefinerService.resultForBidderNames(biddersToSync, tcfContext, accountGdprConfig)
                    .map(tcfResponse -> rejectedRequestBiddersToSync(tcfResponse, cookieSyncContext, biddersToSync));
        } else {
            // Reject all bidders when Host TCF response has blocked pixel
            return Future.succeededFuture(RejectedBidders.of(biddersToSync, Collections.emptySet()));
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
                    .filter(bidder -> bidderCatalog.bidderInfoByName(bidderNameFor(bidder)).isCcpaEnforced())
                    .collect(Collectors.toSet());
        }
        return Collections.emptySet();
    }

    private void respondByRejectedBidder(AsyncResult<RejectedBidders> rejectedBiddersResult,
                                         Set<String> biddersToSync,
                                         CookieSyncContext cookieSyncContext) {
        if (rejectedBiddersResult.succeeded()) {
            final RejectedBidders rejectedBidders = rejectedBiddersResult.result();
            respondWithRejectedBidders(cookieSyncContext, biddersToSync, rejectedBidders);
        } else {
            final Throwable error = rejectedBiddersResult.cause();
            final RoutingContext routingContext = cookieSyncContext.getRoutingContext();
            final TcfContext tcfContext = cookieSyncContext.getPrivacyContext().getTcfContext();
            handleErrors(error, routingContext, tcfContext);
        }
    }

    /**
     * Make HTTP response for given bidders.
     */
    private void respondWithRejectedBidders(CookieSyncContext cookieSyncContext,
                                            Collection<String> bidders,
                                            RejectedBidders rejectedBidders) {
        updateCookieSyncTcfMetrics(bidders, rejectedBidders.getRejectedByTcf());

        final RoutingContext routingContext = cookieSyncContext.getRoutingContext();
        final UidsCookie uidsCookie = cookieSyncContext.getUidsCookie();
        final Privacy privacy = cookieSyncContext.getPrivacyContext().getPrivacy();
        final List<BidderUsersyncStatus> bidderStatuses = bidders.stream()
                .map(bidder -> bidderStatusFor(bidder, routingContext, uidsCookie, rejectedBidders, privacy))
                .filter(Objects::nonNull) // skip bidder with live UID
                .collect(Collectors.toList());

        updateCookieSyncMatchMetrics(bidders, bidderStatuses);

        final CookieSyncRequest cookieSyncRequest = cookieSyncContext.getCookieSyncRequest();
        final Integer limit = cookieSyncRequest.getLimit();
        final List<BidderUsersyncStatus> updatedBidderStatuses = trimBiddersToLimit(limit, bidderStatuses);
        final String status = uidsCookie.hasLiveUids() ? "ok" : "no_cookie";
        final CookieSyncResponse response = CookieSyncResponse.of(status, updatedBidderStatuses);

        final String body = mapper.encode(response);
        respondWith(routingContext, HttpResponseStatus.OK.code(), body, JSON_HEADERS_MAP);

        final TcfContext tcfContext = cookieSyncContext.getPrivacyContext().getTcfContext();
        analyticsDelegator.processEvent(CookieSyncEvent.builder()
                .status(HttpResponseStatus.OK.code())
                .bidderStatus(updatedBidderStatuses)
                .build(), tcfContext);
    }

    private void updateCookieSyncTcfMetrics(Collection<String> syncBidders, Collection<String> rejectedBidders) {
        for (String bidder : syncBidders) {
            if (rejectedBidders.contains(bidder)) {
                metrics.updateCookieSyncTcfBlockedMetric(bidder);
            } else {
                metrics.updateCookieSyncGenMetric(bidder);
            }
        }
    }

    /**
     * Creates {@link BidderUsersyncStatus} for given bidder.
     */
    private BidderUsersyncStatus bidderStatusFor(String bidder,
                                                 RoutingContext context,
                                                 UidsCookie uidsCookie,
                                                 RejectedBidders rejectedBidders,
                                                 Privacy privacy) {

        final boolean isNotAlias = !bidderCatalog.isAlias(bidder);
        final Set<String> biddersRejectedByTcf = rejectedBidders.getRejectedByTcf();
        final Set<String> biddersRejectedByCcpa = rejectedBidders.getRejectedByCcpa();

        if (isNotAlias && !bidderCatalog.isValidName(bidder)) {
            return bidderStatusBuilder(bidder)
                    .error("Unsupported bidder")
                    .build();
        } else if (isNotAlias && !bidderCatalog.isActive(bidder)) {
            return bidderStatusBuilder(bidder)
                    .error(String.format("%s is not configured properly on this Prebid Server deploy. "
                            + "If you believe this should work, contact the company hosting the service "
                            + "and tell them to check their configuration.", bidder))
                    .build();
        } else if (isNotAlias && biddersRejectedByTcf.contains(bidder)) {
            return bidderStatusBuilder(bidder)
                    .error(REJECTED_BY_TCF)
                    .build();
        } else if (isNotAlias && biddersRejectedByCcpa.contains(bidder)) {
            return bidderStatusBuilder(bidder)
                    .error(REJECTED_BY_CCPA)
                    .build();
        } else {
            final Usersyncer usersyncer = bidderCatalog.usersyncerByName(bidderNameFor(bidder));

            if (StringUtils.isEmpty(usersyncer.getUsersyncUrl())) {
                // there is nothing to sync
                return null;
            }

            final UsersyncInfo hostBidderUsersyncInfo = hostBidderUsersyncInfo(context, privacy, usersyncer);

            if (hostBidderUsersyncInfo != null || !uidsCookie.hasLiveUidFrom(usersyncer.getCookieFamilyName())) {
                return bidderStatusBuilder(bidder)
                        .noCookie(true)
                        .usersync(ObjectUtils.defaultIfNull(
                                hostBidderUsersyncInfo,
                                UsersyncInfoAssembler.from(usersyncer).withPrivacy(privacy).assemble()))
                        .build();
            }
        }

        return null;
    }

    private static BidderUsersyncStatus.BidderUsersyncStatusBuilder bidderStatusBuilder(String bidder) {
        return BidderUsersyncStatus.builder().bidder(bidder);
    }

    /**
     * Returns {@link UsersyncInfo} with updated usersync-url (pointed directly to Prebid Server /setuid endpoint)
     * or null if normal usersync flow should be applied.
     * <p>
     * Uids cookie should be in sync with host-cookie value, so the next conditions must be satisfied:
     * <p>
     * 1. Given {@link Usersyncer} should have the same cookie family value as configured host-cookie-family.
     * <p>
     * 2. Host-cookie must be present in HTTP request.
     * <p>
     * 3. Host-bidder uid value in uids cookie should not exist or be different from host-cookie uid value.
     */
    private UsersyncInfo hostBidderUsersyncInfo(RoutingContext context, Privacy privacy, Usersyncer usersyncer) {
        final String cookieFamilyName = usersyncer.getCookieFamilyName();
        if (Objects.equals(cookieFamilyName, uidsCookieService.getHostCookieFamily())) {

            final Map<String, String> cookies = HttpUtil.cookiesAsMap(context);
            final String hostCookieUid = uidsCookieService.parseHostCookie(cookies);

            if (hostCookieUid != null) {
                final Uids parsedUids = uidsCookieService.parseUids(cookies);
                final Map<String, UidWithExpiry> uidsMap = parsedUids != null ? parsedUids.getUids() : null;
                final UidWithExpiry uidWithExpiry = uidsMap != null ? uidsMap.get(cookieFamilyName) : null;
                final String uid = uidWithExpiry != null ? uidWithExpiry.getUid() : null;

                if (!Objects.equals(hostCookieUid, uid)) {
                    final String url = String.format("%s/setuid?bidder=%s&gdpr={{gdpr}}&gdpr_consent={{gdpr_consent}}"
                                    + "&us_privacy={{us_privacy}}&uid=%s", externalUrl, cookieFamilyName,
                            HttpUtil.encodeUrl(hostCookieUid));
                    return UsersyncInfoAssembler.from(usersyncer)
                            .withUrl(url)
                            .withPrivacy(privacy)
                            .assemble();
                }
            }
        }
        return null;
    }

    private void updateCookieSyncMatchMetrics(Collection<String> syncBidders,
                                              Collection<BidderUsersyncStatus> requiredUsersyncs) {
        syncBidders.stream()
                .filter(bidder -> requiredUsersyncs.stream().noneMatch(usersync -> bidder.equals(usersync.getBidder())))
                .forEach(metrics::updateCookieSyncMatchesMetric);
    }

    private static List<BidderUsersyncStatus> trimBiddersToLimit(Integer limit,
                                                                 List<BidderUsersyncStatus> bidderStatuses) {
        if (limit != null && limit > 0 && limit < bidderStatuses.size()) {
            Collections.shuffle(bidderStatuses);
            return bidderStatuses.subList(0, limit);
        } else {
            return bidderStatuses;
        }
    }

    private void handleErrors(Throwable error, RoutingContext routingContext, TcfContext tcfContext) {
        final String message = error.getMessage();
        final int status;
        final String body;
        if (error instanceof InvalidRequestException) {
            metrics.updateUserSyncBadRequestMetric();
            status = HttpResponseStatus.BAD_REQUEST.code();
            body = String.format("Invalid request format: %s", message);
            logger.info(message, error);

        } else if (error instanceof UnauthorizedUidsException) {
            metrics.updateUserSyncOptoutMetric();
            status = HttpResponseStatus.UNAUTHORIZED.code();
            body = String.format("Unauthorized: %s", message);

        } else {
            status = HttpResponseStatus.INTERNAL_SERVER_ERROR.code();
            body = String.format("Unexpected setuid processing error: %s", message);
            logger.warn(body, error);
        }

        respondWith(routingContext, status, body, Collections.emptyMap());
        if (tcfContext == null) {
            analyticsDelegator.processEvent(CookieSyncEvent.error(status, body));
        } else {
            analyticsDelegator.processEvent(CookieSyncEvent.error(status, body), tcfContext);
        }
    }

    private static void respondWith(RoutingContext context,
                                    int status,
                                    String body,
                                    Map<CharSequence, AsciiString> headers) {
        // don't send the response if client has gone
        final HttpServerResponse response = context.response();
        if (response.closed()) {
            logger.warn("The client already closed connection, response will be skipped");
            return;
        }

        response.setStatusCode(status);
        if (body != null) {
            headers.forEach(response::putHeader);
            response.end(body);
        } else {
            response.end();
        }
    }

    @Value(staticConstructor = "of")
    private static class RejectedBidders {

        Set<String> rejectedByTcf;

        Set<String> rejectedByCcpa;
    }
}
