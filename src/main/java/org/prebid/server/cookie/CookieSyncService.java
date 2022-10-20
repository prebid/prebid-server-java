package org.prebid.server.cookie;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.PrivacyEnforcementService;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.BidderInfo;
import org.prebid.server.bidder.UsersyncInfoBuilder;
import org.prebid.server.bidder.UsersyncMethod;
import org.prebid.server.bidder.UsersyncUtil;
import org.prebid.server.cookie.exception.CookieSyncException;
import org.prebid.server.cookie.exception.InvalidCookieSyncRequestException;
import org.prebid.server.cookie.model.BiddersContext;
import org.prebid.server.cookie.model.CookieSyncContext;
import org.prebid.server.cookie.model.RejectionReason;
import org.prebid.server.exception.UnauthorizedUidsException;
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.HostVendorTcfDefinerService;
import org.prebid.server.privacy.gdpr.model.HostVendorTcfResponse;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.gdpr.model.TcfResponse;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.proto.request.CookieSyncRequest;
import org.prebid.server.proto.response.BidderUsersyncStatus;
import org.prebid.server.proto.response.CookieSyncResponse;
import org.prebid.server.proto.response.UsersyncInfo;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountCookieSyncConfig;
import org.prebid.server.settings.model.AccountGdprConfig;
import org.prebid.server.settings.model.AccountPrivacyConfig;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class CookieSyncService {

    private final String externalUrl;
    private final int defaultLimit;
    private final int maxLimit;

    private final BidderCatalog bidderCatalog;
    private final HostVendorTcfDefinerService tcfDefinerService;
    private final PrivacyEnforcementService privacyEnforcementService;
    private final UidsCookieService uidsCookieService;
    private final CoopSyncProvider coopSyncProvider;
    private final Metrics metrics;

    public CookieSyncService(String externalUrl,
                             int defaultLimit,
                             int maxLimit,
                             BidderCatalog bidderCatalog,
                             HostVendorTcfDefinerService tcfDefinerService,
                             PrivacyEnforcementService privacyEnforcementService,
                             UidsCookieService uidsCookieService,
                             CoopSyncProvider coopSyncProvider,
                             Metrics metrics) {

        this.externalUrl = HttpUtil.validateUrl(Objects.requireNonNull(externalUrl));
        this.defaultLimit = defaultLimit;
        this.maxLimit = maxLimit;

        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.tcfDefinerService = Objects.requireNonNull(tcfDefinerService);
        this.privacyEnforcementService = Objects.requireNonNull(privacyEnforcementService);
        this.uidsCookieService = Objects.requireNonNull(uidsCookieService);
        this.coopSyncProvider = Objects.requireNonNull(coopSyncProvider);
        this.metrics = Objects.requireNonNull(metrics);
    }

    public Future<CookieSyncContext> prepareContext(CookieSyncContext cookieSyncContext) {
        return Future.succeededFuture(cookieSyncContext)
                .map(this::validateCookieSyncContext)
                .map(this::resolveLimit)
                .map(this::resolveBiddersToSync)
                .map(this::filterInvalidBidders)
                .map(this::filterDisabledBidders)
                .map(this::applyRequestFilterSettings)
                .compose(this::applyPrivacyFilteringRules)
                .map(this::filterInSyncBidders);
    }

    private CookieSyncContext validateCookieSyncContext(CookieSyncContext cookieSyncContext) {
        final UidsCookie uidsCookie = cookieSyncContext.getUidsCookie();
        if (!uidsCookie.allowsSync()) {
            throw new UnauthorizedUidsException("Sync is not allowed for this uids");
        }

        final CookieSyncRequest cookieSyncRequest = cookieSyncContext.getCookieSyncRequest();
        if (isGdprParamsInconsistent(cookieSyncRequest)) {
            throw new InvalidCookieSyncRequestException("gdpr_consent is required if gdpr is 1");
        }

        final TcfContext tcfContext = cookieSyncContext.getPrivacyContext().getTcfContext();
        if (tcfContext.isInGdprScope() && !tcfContext.isConsentValid()) {
            metrics.updateUserSyncTcfInvalidMetric();
            throw new InvalidCookieSyncRequestException("Consent string is invalid");
        }

        return cookieSyncContext;
    }

    private static boolean isGdprParamsInconsistent(CookieSyncRequest request) {
        return Objects.equals(request.getGdpr(), 1) && StringUtils.isBlank(request.getGdprConsent());
    }

    private CookieSyncContext resolveLimit(CookieSyncContext cookieSyncContext) {
        final AccountCookieSyncConfig accountCookieSyncConfig = cookieSyncContext.getAccount().getCookieSync();

        final int resolvedLimit = ObjectUtils.firstNonNull(
                cookieSyncContext.getCookieSyncRequest().getLimit(),
                ObjectUtil.getIfNotNull(accountCookieSyncConfig, AccountCookieSyncConfig::getDefaultLimit),
                defaultLimit);
        final int adjustedLimit = resolvedLimit <= 0 ? Integer.MAX_VALUE : resolvedLimit;

        final int resolvedMaxLimit = ObjectUtils.firstNonNull(
                ObjectUtil.getIfNotNull(accountCookieSyncConfig, AccountCookieSyncConfig::getMaxLimit),
                maxLimit);
        final int adjustedMaxLimit = resolvedMaxLimit <= 0 ? Integer.MAX_VALUE : resolvedMaxLimit;

        return cookieSyncContext.with(Math.min(adjustedLimit, adjustedMaxLimit));
    }

    private CookieSyncContext resolveBiddersToSync(CookieSyncContext cookieSyncContext) {
        // TODO: Add multisync bidders from 1.a)
        // TODO: filter that are done with multisync
        final BiddersContext updatedContext = cookieSyncContext.getBiddersContext().toBuilder()
                .requestedBidders(resolveBiddersFromRequest(cookieSyncContext))
                .coopSyncBidders(coopSyncProvider.coopSyncBidders(cookieSyncContext))
                .multiSyncBidders(Set.of())
                .build();

        return cookieSyncContext.with(updatedContext);
    }

    private static Set<String> resolveBiddersFromRequest(CookieSyncContext cookieSyncContext) {
        final UidsCookie uidsCookie = cookieSyncContext.getUidsCookie();
        final Set<String> biddersFromRequest = new HashSet<>(CollectionUtils.emptyIfNull(cookieSyncContext.getCookieSyncRequest().getBidders()));
        biddersFromRequest.removeIf(uidsCookie::hasLiveUidFrom);

        return biddersFromRequest;
    }

    private CookieSyncContext filterInvalidBidders(CookieSyncContext cookieSyncContext) {
        return filterBidders(
                cookieSyncContext,
                bidder -> !bidderCatalog.isValidName(bidder),
                RejectionReason.INVALID_BIDDER);
    }

    private CookieSyncContext filterDisabledBidders(CookieSyncContext cookieSyncContext) {
        return filterBidders(
                cookieSyncContext,
                bidder -> !bidderCatalog.isActive(bidder),
                RejectionReason.DISABLED_BIDDER);
    }

    private CookieSyncContext filterInSyncBidders(CookieSyncContext cookieSyncContext) {
        return filterBidders(
                cookieSyncContext,
                bidder -> isBidderInSync(cookieSyncContext, bidder),
                RejectionReason.ALREADY_IN_SYNC);
    }

    private boolean isBidderInSync(CookieSyncContext cookieSyncContext, String bidder) {
        final RoutingContext routingContext = cookieSyncContext.getRoutingContext();
        final String cookieFamilyName = bidderCatalog.cookieFamilyName(bidder).orElseThrow();
        final String uidFromHostCookie = uidsCookieService.uidFromHostCookieToSync(routingContext, cookieFamilyName);

        return StringUtils.isEmpty(uidFromHostCookie)
                && cookieSyncContext.getUidsCookie().hasLiveUidFrom(cookieFamilyName);
    }

    private CookieSyncContext filterBidders(CookieSyncContext cookieSyncContext,
                                            Predicate<String> bidderPredicate,
                                            RejectionReason reason) {

        final BiddersContext biddersContext = cookieSyncContext.getBiddersContext();
        final Set<String> allowedBidders = biddersContext.allowedBidders();

        final Set<String> rejectedBidders = allowedBidders.stream()
                .filter(bidderPredicate)
                .collect(Collectors.toSet());

        final BiddersContext updatedBiddersContext = biddersContext.withRejectedBidders(rejectedBidders, reason);
        return cookieSyncContext.with(updatedBiddersContext);
    }

    private CookieSyncContext applyRequestFilterSettings(CookieSyncContext cookieSyncContext) {
        BiddersContext biddersContext = cookieSyncContext.getBiddersContext();
        final Set<String> allowedBidders = biddersContext.allowedBidders();

        for (String bidder : allowedBidders) {
            final UsersyncMethod usersyncMethod = bidderCatalog.usersyncerByName(bidder)
                    .map(syncer -> cookieSyncContext.getUsersyncMethodChooser().choose(syncer, bidder))
                    .orElse(null);

            if (usersyncMethod != null) {
                biddersContext = biddersContext.withBidderUsersyncMethod(bidder, usersyncMethod);
            } else {
                biddersContext = biddersContext.withRejectedBidder(bidder, RejectionReason.REJECTED_BY_FILTER);
                metrics.updateCookieSyncFilteredMetric(bidder);
            }
        }

        return cookieSyncContext.with(biddersContext);
    }

    private Future<CookieSyncContext> applyPrivacyFilteringRules(CookieSyncContext cookieSyncContext) {
        final TcfContext tcfContext = cookieSyncContext.getPrivacyContext().getTcfContext();
        return tcfDefinerService.isAllowedForHostVendorId(tcfContext)
                .compose(hostTcfResponse -> filterWithTcfResponse(hostTcfResponse, cookieSyncContext))
                .otherwise(error -> rethrowAsCookieSyncException(error, tcfContext));
    }

    private Future<CookieSyncContext> filterWithTcfResponse(HostVendorTcfResponse hostVendorTcfResponse,
                                                            CookieSyncContext cookieSyncContext) {

        final BiddersContext biddersContext = cookieSyncContext.getBiddersContext();
        // Host vendor tcf response can be not populated if host vendor id is not defined,
        // we can't be sure if we can use it. So we get tcf values from response for all bidders.
        if (!hostVendorTcfResponse.isVendorAllowed()) {
            // Reject all bidders when Host TCF response has blocked pixel
            final BiddersContext rejectedContext = biddersContext.withRejectedBidders(
                    biddersContext.allowedBidders(), RejectionReason.REJECTED_BY_TCF);

            return Future.succeededFuture(cookieSyncContext.with(rejectedContext));
        }

        final CookieSyncContext updatedContext = cookieSyncContext.with(biddersContext);

        final TcfContext tcfContext = updatedContext.getPrivacyContext().getTcfContext();
        final AccountPrivacyConfig accountPrivacyConfig = updatedContext.getAccount().getPrivacy();
        final AccountGdprConfig accountGdprConfig =
                accountPrivacyConfig != null ? accountPrivacyConfig.getGdpr() : null;
        final Set<String> bidders = new HashSet<>(biddersContext.requestedBidders());

        return tcfDefinerService.resultForBidderNames(bidders, tcfContext, accountGdprConfig)
                .map(tcfResponse -> updateWithPrivacy(tcfResponse, updatedContext));
    }

    private CookieSyncContext updateWithPrivacy(TcfResponse<String> tcfResponse, CookieSyncContext cookieSyncContext) {
        final BiddersContext biddersContext = cookieSyncContext.getBiddersContext();
        final Set<String> requestedBidders = biddersContext.requestedBidders();

        final Account account = cookieSyncContext.getAccount();
        final Privacy privacy = cookieSyncContext.getPrivacyContext().getPrivacy();
        final Set<String> rejectedByCcpaBidders = extractCcpaEnforcedBidders(account, requestedBidders, privacy);

        final Map<String, PrivacyEnforcementAction> bidderToAction = tcfResponse.getActions();
        final Set<String> rejectedByTcfBidders = requestedBidders.stream()
                .filter(bidder -> !rejectedByCcpaBidders.contains(bidder))
                .filter(bidder -> !bidderToAction.containsKey(bidder) || bidderToAction.get(bidder).isBlockPixelSync())
                .collect(Collectors.toSet());

        final BiddersContext privacyUpdatedBiddersContext = biddersContext
                .withRejectedBidders(rejectedByCcpaBidders, RejectionReason.REJECTED_BY_CCPA)
                .withRejectedBidders(rejectedByTcfBidders, RejectionReason.REJECTED_BY_TCF);

        return cookieSyncContext.with(privacyUpdatedBiddersContext);
    }

    private Set<String> extractCcpaEnforcedBidders(Account account, Collection<String> biddersToSync, Privacy privacy) {
        if (privacyEnforcementService.isCcpaEnforced(privacy.getCcpa(), account)) {
            return biddersToSync.stream()
                    .filter(this::isBidderCcpaEnforced)
                    .collect(Collectors.toSet());
        }
        return Collections.emptySet();
    }

    private boolean isBidderCcpaEnforced(String bidder) {
        final BidderInfo bidderInfo = bidderCatalog.bidderInfoByName(bidder);
        return bidderInfo != null && bidderInfo.isCcpaEnforced();
    }

    public CookieSyncResponse prepareResponse(CookieSyncContext cookieSyncContext) {
        final BiddersContext biddersContext = cookieSyncContext.getBiddersContext();
        updateCookieSyncTcfMetrics(biddersContext);

        final UidsCookie uidsCookie = cookieSyncContext.getUidsCookie();
        final List<BidderUsersyncStatus> allowedStatuses = statusesForAllowedBidders(cookieSyncContext);
        final List<BidderUsersyncStatus> rejectedStatuses = statusesForRejectedBidders(cookieSyncContext);

//        updateCookieSyncMatchMetrics(bidders, bidderStatuses);

        final String cookieSyncStatus = uidsCookie.hasLiveUids() ? "ok" : "no_cookie";
        return CookieSyncResponse.of(cookieSyncStatus, ListUtils.union(allowedStatuses, rejectedStatuses));
    }

    private List<BidderUsersyncStatus> statusesForAllowedBidders(CookieSyncContext cookieSyncContext) {
        final BiddersContext biddersContext = cookieSyncContext.getBiddersContext();
        final int limit = cookieSyncContext.getLimit();

        final Set<String> allowedBiddersWithPriority = new LinkedHashSet<>();
        allowedBiddersWithPriority.addAll(biddersContext.allowedRequestedBidders());
        allowedBiddersWithPriority.addAll(biddersContext.allowedCoopSyncBidders());

        return allowedBiddersWithPriority.stream()
                .map(bidder -> statusForAllowedBidder(bidder, cookieSyncContext))
                .limit(limit)
                .toList();
    }

    private BidderUsersyncStatus statusForAllowedBidder(String bidder, CookieSyncContext cookieSyncContext) {
        final BiddersContext biddersContext = cookieSyncContext.getBiddersContext();
        final RoutingContext routingContext = cookieSyncContext.getRoutingContext();

        final UsersyncMethod usersyncMethod = biddersContext.bidderUsersyncMethod().get(bidder);
        final Privacy privacy = cookieSyncContext.getPrivacyContext().getPrivacy();
        final String cookieFamilyName = bidderCatalog.cookieFamilyName(bidder).orElseThrow();
        final String uidFromHostCookie = uidsCookieService.uidFromHostCookieToSync(routingContext, cookieFamilyName);

        final UsersyncInfo usersyncInfo = toUsersyncInfo(usersyncMethod, cookieFamilyName, uidFromHostCookie, privacy);

        return BidderUsersyncStatus.builder()
                .bidder(bidder)
                .noCookie(true)
                .usersync(usersyncInfo)
                .build();
    }

    private List<BidderUsersyncStatus> statusesForRejectedBidders(CookieSyncContext cookieSyncContext) {
        final BiddersContext biddersContext = cookieSyncContext.getBiddersContext();
        return biddersContext.rejectedBidders().entrySet().stream()
                .map(bidderWithReason ->
                        statusForRejectedBidder(bidderWithReason.getKey(), bidderWithReason.getValue()))
                .filter(status -> status.getError() != null)
                .toList();
    }

    private BidderUsersyncStatus statusForRejectedBidder(String bidder, RejectionReason reason) {
        BidderUsersyncStatus.BidderUsersyncStatusBuilder builder = BidderUsersyncStatus.builder()
                .bidder(bidder);

        builder = switch (reason) {
            case INVALID_BIDDER -> builder.error("Unsupported bidder");
            case DISABLED_BIDDER -> builder.error(bidder + """
                     is not configured properly on this Prebid Server deploy. \
                    If you believe this should work, contact the company hosting \
                    the service and tell them to check their configuration.""");
            case REJECTED_BY_TCF -> builder.error("Rejected by TCF");
            case REJECTED_BY_CCPA -> builder.error("Rejected by CCPA");
            case REJECTED_BY_FILTER, ALREADY_IN_SYNC -> builder;
        };

        return builder.build();
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

    private String toHostBidderUsersyncUrl(String cookieFamilyName,
                                           UsersyncMethod usersyncMethod,
                                           String hostCookieUid) {

        final String url = UsersyncUtil.CALLBACK_URL_TEMPLATE.formatted(
                externalUrl,
                cookieFamilyName,
                HttpUtil.encodeUrl(hostCookieUid));

        return UsersyncUtil.enrichUrlWithFormat(url, UsersyncUtil.resolveFormat(usersyncMethod));
    }

    private void updateCookieSyncTcfMetrics(BiddersContext biddersContext) {
//        biddersContext.tcfRejectedBidders()
//                .forEach(bidder -> metrics.updateCookieSyncTcfBlockedMetric(
//                        bidderCatalog.isValidName(bidder) ? bidder : METRICS_UNKNOWN_BIDDER));
//        biddersContext.tcfAllowedBidders().forEach(metrics::updateCookieSyncGenMetric);
    }

    private void updateCookieSyncMatchMetrics(Collection<String> syncBidders,
                                              Collection<BidderUsersyncStatus> requiredUsersyncs) {
        syncBidders.stream()
                .filter(bidder -> requiredUsersyncs.stream().noneMatch(usersync -> bidder.equals(usersync.getBidder())))
                .forEach(metrics::updateCookieSyncMatchesMetric);
    }

    private static <T> T rethrowAsCookieSyncException(Throwable error, TcfContext tcfContext) {
        throw new CookieSyncException(error, tcfContext);
    }
}
