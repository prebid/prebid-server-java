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
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.model.CookieSyncEvent;
import org.prebid.server.auction.PrivacyEnforcementService;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.cookie.model.UidWithExpiry;
import org.prebid.server.cookie.proto.Uids;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.ccpa.Ccpa;
import org.prebid.server.privacy.gdpr.TcfDefinerService;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.TcfResponse;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.proto.request.CookieSyncRequest;
import org.prebid.server.proto.response.BidderUsersyncStatus;
import org.prebid.server.proto.response.CookieSyncResponse;
import org.prebid.server.proto.response.UsersyncInfo;
import org.prebid.server.settings.ApplicationSettings;
import org.prebid.server.settings.model.Account;
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
    private final boolean useGeoLocation;
    private final boolean defaultCoopSync;
    private final List<Collection<String>> listOfCoopSyncBidders;
    private final AnalyticsReporter analyticsReporter;
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
                             boolean useGeoLocation,
                             boolean defaultCoopSync,
                             List<Collection<String>> listOfCoopSyncBidders,
                             AnalyticsReporter analyticsReporter,
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
        this.gdprHostVendorId = gdprHostVendorId;
        this.useGeoLocation = useGeoLocation;
        this.defaultCoopSync = defaultCoopSync;
        this.listOfCoopSyncBidders = CollectionUtils.isNotEmpty(listOfCoopSyncBidders)
                ? listOfCoopSyncBidders
                : Collections.singletonList(activeBidders);
        this.analyticsReporter = Objects.requireNonNull(analyticsReporter);
        this.metrics = Objects.requireNonNull(metrics);
        this.timeoutFactory = Objects.requireNonNull(timeoutFactory);
        this.mapper = Objects.requireNonNull(mapper);
    }

    private static Set<String> activeBidders(BidderCatalog bidderCatalog) {
        return bidderCatalog.names().stream().filter(bidderCatalog::isActive).collect(Collectors.toSet());
    }

    @Override
    public void handle(RoutingContext context) {
        metrics.updateCookieSyncRequestMetric();

        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(context);
        if (!uidsCookie.allowsSync()) {
            final int status = HttpResponseStatus.UNAUTHORIZED.code();
            final String message = "User has opted out";
            context.response().setStatusCode(status).setStatusMessage(message).end();
            analyticsReporter.processEvent(CookieSyncEvent.error(status, message));
            return;
        }

        final Buffer body = context.getBody();
        if (body == null) {
            final int status = HttpResponseStatus.BAD_REQUEST.code();
            final String message = "Request has no body";
            context.response().setStatusCode(status).setStatusMessage(message).end();
            analyticsReporter.processEvent(CookieSyncEvent.error(status, message));
            return;
        }

        final CookieSyncRequest cookieSyncRequest;
        try {
            cookieSyncRequest = mapper.decodeValue(body, CookieSyncRequest.class);
        } catch (DecodeException e) {
            final int status = HttpResponseStatus.BAD_REQUEST.code();
            final String message = "Request body cannot be parsed";
            context.response().setStatusCode(status).setStatusMessage(message).end();
            analyticsReporter.processEvent(CookieSyncEvent.error(status, message));
            logger.info(message, e);
            return;
        }

        final Integer gdpr = cookieSyncRequest.getGdpr();
        final String gdprConsent = cookieSyncRequest.getGdprConsent();
        if (Objects.equals(gdpr, 1) && StringUtils.isBlank(gdprConsent)) {
            final int status = HttpResponseStatus.BAD_REQUEST.code();
            final String message = "gdpr_consent is required if gdpr is 1";
            context.response().setStatusCode(status).setStatusMessage(message).end();
            analyticsReporter.processEvent(CookieSyncEvent.error(status, message));
            return;
        }

        final Integer limit = cookieSyncRequest.getLimit();
        final Boolean coopSync = cookieSyncRequest.getCoopSync();
        final Set<String> biddersToSync = biddersToSync(cookieSyncRequest.getBidders(), coopSync, limit);

        final String gdprAsString = gdpr != null ? gdpr.toString() : null;
        final Ccpa ccpa = Ccpa.of(cookieSyncRequest.getUsPrivacy());
        final Privacy privacy = Privacy.of(gdprAsString, gdprConsent, ccpa);

        if (privacyEnforcementService.isCcpaEnforced(ccpa)) {
            respondWith(context, uidsCookie, privacy, biddersToSync, biddersToSync, limit, REJECTED_BY_CCPA);
            return;
        }

        final String requestAccount = cookieSyncRequest.getAccount();
        final Set<Integer> vendorIds = Collections.singleton(gdprHostVendorId);
        final String ip = useGeoLocation ? HttpUtil.ipFrom(context.request()) : null;
        final Timeout timeout = timeoutFactory.create(defaultTimeout);

        accountById(requestAccount, timeout)
                .compose(account -> tcfDefinerService.resultFor(vendorIds, biddersToSync, gdprAsString, gdprConsent,
                        ip, account, timeout))
                .setHandler(asyncResult ->
                        handleResult(asyncResult, context, uidsCookie, biddersToSync, privacy, limit));
    }

    /**
     * Returns bidder names to sync.
     * <p>
     * If bidder list was omitted in request, that means sync should be done for all bidders.
     */
    private Set<String> biddersToSync(List<String> requestBidders, Boolean requestCoop, Integer requestLimit) {
        if (CollectionUtils.isEmpty(requestBidders)) {
            return activeBidders;
        }

        final boolean coop = requestCoop != null ? requestCoop : defaultCoopSync;
        if (coop) {
            return requestLimit == null
                    ? addAllCoopSyncBidders(requestBidders)
                    : addCoopSyncBidders(requestBidders, requestLimit);
        }

        return new HashSet<>(requestBidders);
    }

    private Set<String> addAllCoopSyncBidders(List<String> bidders) {
        final Set<String> updatedBidders = listOfCoopSyncBidders.stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());

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

    /**
     * Handles TCF verification result.
     */
    private void handleResult(AsyncResult<TcfResponse> asyncResult, RoutingContext context, UidsCookie uidsCookie,
                              Collection<String> biddersToSync, Privacy privacy, Integer limit) {
        if (asyncResult.failed()) {
            respondWith(context, uidsCookie, privacy, biddersToSync, biddersToSync, limit, REJECTED_BY_TCF);
        } else {
            final TcfResponse tcfResponse = asyncResult.result();

            final Map<Integer, PrivacyEnforcementAction> vendorIdToAction = tcfResponse.getVendorIdToActionMap();
            final PrivacyEnforcementAction hostActions = vendorIdToAction != null
                    ? vendorIdToAction.get(gdprHostVendorId)
                    : null;
            if (hostActions == null || hostActions.isBlockPixelSync()) {
                // host vendor should be allowed by TCF verification
                respondWith(context, uidsCookie, privacy, biddersToSync, biddersToSync, limit, REJECTED_BY_TCF);
            } else {
                final Map<String, PrivacyEnforcementAction> bidderNameToAction = tcfResponse.getBidderNameToActionMap();

                final Set<String> biddersRejectedByTcf = biddersToSync.stream()
                        .filter(bidder ->
                                !bidderNameToAction.containsKey(bidder)
                                        || bidderNameToAction.get(bidder).isBlockPixelSync())
                        .collect(Collectors.toSet());

                respondWith(context, uidsCookie, privacy, biddersToSync, biddersRejectedByTcf, limit, REJECTED_BY_TCF);
            }
        }
    }

    /**
     * Make HTTP response for given bidders.
     */
    private void respondWith(RoutingContext context, UidsCookie uidsCookie, Privacy privacy, Collection<String> bidders,
                             Collection<String> biddersRejectedByTcf, Integer limit, String rejectMessage) {
        updateCookieSyncTcfMetrics(bidders, biddersRejectedByTcf);

        final List<BidderUsersyncStatus> bidderStatuses = bidders.stream()
                .map(bidder -> bidderStatusFor(bidder, context, uidsCookie, biddersRejectedByTcf, privacy,
                        rejectMessage))
                .filter(Objects::nonNull) // skip bidder with live UID
                .collect(Collectors.toList());
        updateCookieSyncMatchMetrics(bidders, bidderStatuses);

        final List<BidderUsersyncStatus> updatedBidderStatuses;
        if (limit != null && limit > 0 && limit < bidderStatuses.size()) {
            Collections.shuffle(bidderStatuses);
            updatedBidderStatuses = bidderStatuses.subList(0, limit);
        } else {
            updatedBidderStatuses = bidderStatuses;
        }

        final CookieSyncResponse response = CookieSyncResponse.of(uidsCookie.hasLiveUids() ? "ok" : "no_cookie",
                updatedBidderStatuses);
        final String body = mapper.encode(response);

        // don't send the response if client has gone
        if (context.response().closed()) {
            logger.warn("The client already closed connection, response will be skipped");
            return;
        }
        context.response()
                .putHeader(HttpUtil.CONTENT_TYPE_HEADER, HttpHeaderValues.APPLICATION_JSON)
                .end(body);

        analyticsReporter.processEvent(CookieSyncEvent.builder()
                .status(HttpResponseStatus.OK.code())
                .bidderStatus(updatedBidderStatuses)
                .build());
    }

    private void updateCookieSyncTcfMetrics(Collection<String> syncBidders, Collection<String> rejectedBidders) {
        for (String bidder : syncBidders) {
            if (rejectedBidders.contains(bidder)) {
                metrics.updateCookieSyncGdprPreventMetric(bidder);
            } else {
                metrics.updateCookieSyncGenMetric(bidder);
            }
        }
    }

    private void updateCookieSyncMatchMetrics(Collection<String> syncBidders,
                                              Collection<BidderUsersyncStatus> requiredUsersyncs) {
        syncBidders.stream()
                .filter(bidder -> requiredUsersyncs.stream().noneMatch(usersync -> bidder.equals(usersync.getBidder())))
                .forEach(metrics::updateCookieSyncMatchesMetric);
    }

    /**
     * Creates {@link BidderUsersyncStatus} for given bidder.
     */
    private BidderUsersyncStatus bidderStatusFor(String bidder, RoutingContext context, UidsCookie uidsCookie,
                                                 Collection<String> biddersRejectedByTcf, Privacy privacy,
                                                 String rejectMessage) {
        final BidderUsersyncStatus result;
        final boolean isNotAlias = !bidderCatalog.isAlias(bidder);

        if (isNotAlias && !bidderCatalog.isValidName(bidder)) {
            result = bidderStatusBuilder(bidder)
                    .error("Unsupported bidder")
                    .build();
        } else if (isNotAlias && !bidderCatalog.isActive(bidder)) {
            result = bidderStatusBuilder(bidder)
                    .error(String.format("%s is not configured properly on this Prebid Server deploy. "
                            + "If you believe this should work, contact the company hosting the service "
                            + "and tell them to check their configuration.", bidder))
                    .build();
        } else if (isNotAlias && biddersRejectedByTcf.contains(bidder)) {
            result = bidderStatusBuilder(bidder)
                    .error(rejectMessage)
                    .build();
        } else {
            final Usersyncer usersyncer = bidderCatalog.usersyncerByName(bidderNameFor(bidder));
            final UsersyncInfo hostBidderUsersyncInfo = hostBidderUsersyncInfo(context, privacy, usersyncer);

            if (hostBidderUsersyncInfo != null || !uidsCookie.hasLiveUidFrom(usersyncer.getCookieFamilyName())) {
                result = bidderStatusBuilder(bidder)
                        .noCookie(true)
                        .usersync(ObjectUtils.defaultIfNull(hostBidderUsersyncInfo,
                                UsersyncInfo.from(usersyncer).withPrivacy(privacy).assemble()))
                        .build();
            } else {
                result = null;
            }
        }

        return result;
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
                    return UsersyncInfo.from(usersyncer).withUrl(url)
                            .withPrivacy(privacy)
                            .assemble();
                }
            }
        }
        return null;
    }

    private Future<Account> accountById(String accountId, Timeout timeout) {
        return StringUtils.isBlank(accountId)
                ? Future.succeededFuture(null)
                : applicationSettings.getAccountById(accountId, timeout)
                        .otherwise((Account) null);
    }
}
