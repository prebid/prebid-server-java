package org.prebid.server.cookie;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.payload.impl.ActivityCallPayloadImpl;
import org.prebid.server.activity.infrastructure.payload.impl.TcfContextActivityCallPayload;
import org.prebid.server.auction.PrivacyEnforcementService;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.BidderInfo;
import org.prebid.server.bidder.UsersyncInfoBuilder;
import org.prebid.server.bidder.UsersyncMethod;
import org.prebid.server.bidder.UsersyncUtil;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.cookie.exception.CookieSyncException;
import org.prebid.server.cookie.exception.InvalidCookieSyncRequestException;
import org.prebid.server.cookie.exception.UnauthorizedUidsException;
import org.prebid.server.cookie.model.BiddersContext;
import org.prebid.server.cookie.model.CookieSyncContext;
import org.prebid.server.cookie.model.CookieSyncStatus;
import org.prebid.server.cookie.model.RejectionReason;
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
import org.prebid.server.spring.config.bidder.model.usersync.CookieFamilySource;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
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
        validateLimits(defaultLimit, maxLimit);
        this.defaultLimit = defaultLimit;
        this.maxLimit = maxLimit;

        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.tcfDefinerService = Objects.requireNonNull(tcfDefinerService);
        this.privacyEnforcementService = Objects.requireNonNull(privacyEnforcementService);
        this.uidsCookieService = Objects.requireNonNull(uidsCookieService);
        this.coopSyncProvider = Objects.requireNonNull(coopSyncProvider);
        this.metrics = Objects.requireNonNull(metrics);
    }

    private static void validateLimits(int limit, int maxLimit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Default cookie-sync limit should be greater than 0");
        } else if (maxLimit < limit) {
            throw new IllegalArgumentException("Max cookie-sync limit should be greater or equal than limit");
        }
    }

    public Future<CookieSyncContext> processContext(CookieSyncContext cookieSyncContext) {
        return Future.succeededFuture(cookieSyncContext)
                .map(this::validateCookieSyncContext)
                .map(this::resolveLimit)
                .map(this::resolveBiddersToSync)
                .map(this::filterInvalidBidders)
                .map(this::filterDisabledBidders)
                .map(this::filterBiddersWithoutUsersync)
                .map(this::filterBiddersWithDisabledUsersync)
                .map(this::applyRequestFilterSettings)
                .compose(this::applyPrivacyFilteringRules)
                .map(this::filterInSyncBidders);
    }

    private CookieSyncContext validateCookieSyncContext(CookieSyncContext cookieSyncContext) {
        final TcfContext tcfContext = cookieSyncContext.getPrivacyContext().getTcfContext();

        final UidsCookie uidsCookie = cookieSyncContext.getUidsCookie();
        if (!uidsCookie.allowsSync()) {
            throw new UnauthorizedUidsException("Sync is not allowed for this uids", tcfContext);
        }

        final CookieSyncRequest cookieSyncRequest = cookieSyncContext.getCookieSyncRequest();
        if (isGdprParamsInconsistent(cookieSyncRequest)) {
            throw new InvalidCookieSyncRequestException("gdpr_consent is required if gdpr is 1", tcfContext);
        }

        if (tcfContext.isInGdprScope() && !tcfContext.isConsentValid()) {
            metrics.updateUserSyncTcfInvalidMetric();
            throw new InvalidCookieSyncRequestException("Consent string is invalid", tcfContext);
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
        final List<String> requestedBiddersAsList = new ArrayList<>(
                SetUtils.emptyIfNull(cookieSyncContext.getCookieSyncRequest().getBidders()));
        Collections.shuffle(requestedBiddersAsList);

        final BiddersContext updatedContext = cookieSyncContext.getBiddersContext().toBuilder()
                .requestedBidders(new LinkedHashSet<>(requestedBiddersAsList))
                .coopSyncBidders(coopSyncProvider.coopSyncBidders(cookieSyncContext))
                .multiSyncBidders(Set.of())
                .build();

        return cookieSyncContext.with(updatedContext);
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

    private CookieSyncContext filterBiddersWithoutUsersync(CookieSyncContext cookieSyncContext) {
        return filterBidders(
                cookieSyncContext,
                bidder -> bidderCatalog.usersyncerByName(bidder).isEmpty(),
                RejectionReason.UNCONFIGURED_USERSYNC);
    }

    private CookieSyncContext filterBiddersWithDisabledUsersync(CookieSyncContext cookieSyncContext) {
        return filterBidders(
                cookieSyncContext,
                bidder -> !bidderCatalog.usersyncerByName(bidder).orElseThrow().isEnabled(),
                RejectionReason.DISABLED_USERSYNC);
    }

    /**
     * should be called after applying request filter, as it will populate usersync data
     */
    private CookieSyncContext filterInSyncBidders(CookieSyncContext cookieSyncContext) {
        return filterBidders(
                cookieSyncContext,
                bidder -> isBidderInSync(cookieSyncContext, bidder),
                RejectionReason.ALREADY_IN_SYNC);
    }

    private boolean isBidderInSync(CookieSyncContext cookieSyncContext, String bidder) {
        final RoutingContext routingContext = cookieSyncContext.getRoutingContext();
        final String cookieFamilyName = bidderCatalog.cookieFamilyName(bidder).orElseThrow();
        final String uidFromHostCookie = uidsCookieService.hostCookieUidToSync(routingContext, cookieFamilyName);

        return StringUtils.isEmpty(uidFromHostCookie)
                && cookieSyncContext.getUidsCookie().hasLiveUidFrom(cookieFamilyName);
    }

    private CookieSyncContext filterBidders(CookieSyncContext cookieSyncContext,
                                            Predicate<String> bidderPredicate,
                                            RejectionReason reason) {

        final BiddersContext biddersContext = cookieSyncContext.getBiddersContext();
        final Set<String> rejectedBidders = biddersContext.allowedBidders().stream()
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
                .onSuccess(updatedContext -> updateCookieSyncTcfMetrics(updatedContext.getBiddersContext()))
                .otherwise(error -> rethrowAsCookieSyncException(error, tcfContext))
                .map(context -> filterDisallowedActivities(context, tcfContext));
    }

    private Future<CookieSyncContext> filterWithTcfResponse(HostVendorTcfResponse hostVendorTcfResponse,
                                                            CookieSyncContext cookieSyncContext) {

        final BiddersContext biddersContext = cookieSyncContext.getBiddersContext();
        final Set<String> allowedBidders = biddersContext.allowedBidders();
        // Host vendor tcf response can be not populated if host vendor id is not defined,
        // we can't be sure if we can use it. So we get tcf values from response for all bidders.
        if (!hostVendorTcfResponse.isVendorAllowed()) {
            // Reject all bidders when Host TCF response has blocked pixel
            final BiddersContext rejectedContext = biddersContext.withRejectedBidders(
                    allowedBidders, RejectionReason.REJECTED_BY_TCF);

            return Future.succeededFuture(cookieSyncContext.with(rejectedContext));
        }

        final CookieSyncContext updatedContext = cookieSyncContext.with(biddersContext);

        final TcfContext tcfContext = updatedContext.getPrivacyContext().getTcfContext();
        final AccountPrivacyConfig accountPrivacyConfig = updatedContext.getAccount().getPrivacy();
        final AccountGdprConfig accountGdprConfig =
                accountPrivacyConfig != null ? accountPrivacyConfig.getGdpr() : null;

        return tcfDefinerService.resultForBidderNames(allowedBidders, tcfContext, accountGdprConfig)
                .map(tcfResponse -> updateWithPrivacy(tcfResponse, updatedContext));
    }

    private CookieSyncContext updateWithPrivacy(TcfResponse<String> tcfResponse, CookieSyncContext cookieSyncContext) {
        final BiddersContext biddersContext = cookieSyncContext.getBiddersContext();
        final Set<String> allowedBidders = biddersContext.allowedBidders();

        final Account account = cookieSyncContext.getAccount();
        final Privacy privacy = cookieSyncContext.getPrivacyContext().getPrivacy();
        final Set<String> rejectedByCcpaBidders = extractCcpaEnforcedBidders(account, allowedBidders, privacy);

        final Map<String, PrivacyEnforcementAction> bidderToAction = tcfResponse.getActions();
        final Set<String> rejectedByTcfBidders = allowedBidders.stream()
                .filter(bidder -> !rejectedByCcpaBidders.contains(bidder))
                .filter(bidder -> !bidderToAction.containsKey(bidder) || bidderToAction.get(bidder).isBlockPixelSync())
                .collect(Collectors.toSet());

        final BiddersContext privacyUpdatedBiddersContext = biddersContext
                .withRejectedBidders(rejectedByCcpaBidders, RejectionReason.REJECTED_BY_CCPA)
                .withRejectedBidders(rejectedByTcfBidders, RejectionReason.REJECTED_BY_TCF);

        return cookieSyncContext.with(privacyUpdatedBiddersContext);
    }

    private Set<String> extractCcpaEnforcedBidders(Account account, Collection<String> biddersToSync, Privacy privacy) {
        if (!privacyEnforcementService.isCcpaEnforced(privacy.getCcpa(), account)) {
            return Collections.emptySet();
        }

        return biddersToSync.stream()
                .filter(this::isBidderCcpaEnforced)
                .collect(Collectors.toSet());
    }

    private boolean isBidderCcpaEnforced(String bidder) {
        final BidderInfo bidderInfo = bidderCatalog.bidderInfoByName(bidder);
        return bidderInfo != null && bidderInfo.isCcpaEnforced();
    }

    private CookieSyncContext filterDisallowedActivities(CookieSyncContext cookieSyncContext, TcfContext tcfContext) {
        return filterBidders(
                cookieSyncContext,
                bidder -> !cookieSyncContext.getActivityInfrastructure().isAllowed(
                        Activity.SYNC_USER,
                        TcfContextActivityCallPayload.of(
                                ActivityCallPayloadImpl.of(ComponentType.BIDDER, bidder),
                                tcfContext)),
                RejectionReason.DISALLOWED_ACTIVITY);
    }

    public CookieSyncResponse prepareResponse(CookieSyncContext cookieSyncContext) {
        final CookieSyncStatus cookieSyncStatus = cookieSyncContext.getUidsCookie().hasLiveUids()
                ? CookieSyncStatus.OK
                : CookieSyncStatus.NO_COOKIE;

        final Set<String> biddersToSync = biddersToSync(cookieSyncContext);

        final List<BidderUsersyncStatus> statuses = ListUtils.union(
                validStatuses(biddersToSync, cookieSyncContext),
                debugStatuses(biddersToSync, cookieSyncContext));

        final List<String> warnings = cookieSyncContext.getWarnings();
        final List<String> resolvedWarnings = CollectionUtils.isNotEmpty(warnings)
                ? warnings
                : null;

        return CookieSyncResponse.of(cookieSyncStatus, Collections.unmodifiableList(statuses), resolvedWarnings);
    }

    private Set<String> biddersToSync(CookieSyncContext cookieSyncContext) {
        final Set<String> allowedBiddersByPriority = allowedBiddersByPriority(cookieSyncContext);

        final Set<String> cookieFamiliesToSync = new HashSet<>(); // multiple bidders may have same cookie families
        final Set<String> biddersToSync = new LinkedHashSet<>();
        final Iterator<String> biddersIterator = allowedBiddersByPriority.iterator();

        while (cookieFamiliesToSync.size() < cookieSyncContext.getLimit() && biddersIterator.hasNext()) {
            final String bidder = biddersIterator.next();
            final String cookieFamilyName = bidderCatalog.cookieFamilyName(bidder).orElseThrow();

            cookieFamiliesToSync.add(cookieFamilyName);
            biddersToSync.add(bidder);
        }

        return biddersToSync;
    }

    private static Set<String> allowedBiddersByPriority(CookieSyncContext cookieSyncContext) {
        final BiddersContext biddersContext = cookieSyncContext.getBiddersContext();

        final Set<String> allowedBiddersByPriority = new LinkedHashSet<>();
        allowedBiddersByPriority.addAll(biddersContext.allowedRequestedBidders());
        allowedBiddersByPriority.addAll(biddersContext.allowedCoopSyncBidders());

        return allowedBiddersByPriority;
    }

    private List<BidderUsersyncStatus> validStatuses(Set<String> biddersToSync, CookieSyncContext cookieSyncContext) {
        return biddersToSync.stream()
                .filter(distinctBy(bidder -> bidderCatalog.cookieFamilyName(bidder).orElseThrow()))
                .map(bidder -> validStatus(bidder, cookieSyncContext))
                .toList();
    }

    private static <T> Predicate<T> distinctBy(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = new HashSet<>();
        return value -> seen.add(keyExtractor.apply(value));
    }

    private BidderUsersyncStatus validStatus(String bidder, CookieSyncContext cookieSyncContext) {
        final BiddersContext biddersContext = cookieSyncContext.getBiddersContext();
        final RoutingContext routingContext = cookieSyncContext.getRoutingContext();
        final String cookieFamilyName = bidderCatalog.cookieFamilyName(bidder).orElseThrow();

        final UsersyncMethod usersyncMethod = biddersContext.bidderUsersyncMethod().get(bidder);
        final Privacy privacy = cookieSyncContext.getPrivacyContext().getPrivacy();
        final String hostCookieUid = uidsCookieService.hostCookieUidToSync(routingContext, cookieFamilyName);

        final UsersyncInfo usersyncInfo = toUsersyncInfo(usersyncMethod, cookieFamilyName, hostCookieUid, privacy);

        return BidderUsersyncStatus.builder()
                .bidder(cookieFamilyName) // we are syncing cookie-family-names instead of bidder codes
                .noCookie(true)
                .usersync(usersyncInfo)
                .build();
    }

    private UsersyncInfo toUsersyncInfo(UsersyncMethod usersyncMethod,
                                        String cookieFamilyName,
                                        String hostCookieUid,
                                        Privacy privacy) {

        final UsersyncInfoBuilder usersyncInfoBuilder = UsersyncInfoBuilder.from(usersyncMethod);

        if (hostCookieUid != null) {
            final String url = UsersyncUtil.CALLBACK_URL_TEMPLATE.formatted(
                    externalUrl, HttpUtil.encodeUrl(cookieFamilyName), HttpUtil.encodeUrl(hostCookieUid));

            usersyncInfoBuilder
                    .usersyncUrl(UsersyncUtil.enrichUrlWithFormat(url, UsersyncUtil.resolveFormat(usersyncMethod)))
                    .redirectUrl(null);
        }

        return usersyncInfoBuilder
                .privacy(privacy)
                .build();
    }

    private List<BidderUsersyncStatus> debugStatuses(Set<String> biddersToSync, CookieSyncContext cookieSyncContext) {
        if (!cookieSyncContext.isDebug()) {
            return Collections.emptyList();
        }

        final List<BidderUsersyncStatus> debugStatuses = new ArrayList<>();
        debugStatuses.addAll(rejectionStatuses(cookieSyncContext));
        debugStatuses.addAll(limitStatuses(biddersToSync, cookieSyncContext));
        debugStatuses.addAll(aliasSyncedAsRootStatuses(biddersToSync, cookieSyncContext));

        return debugStatuses;
    }

    private List<BidderUsersyncStatus> rejectionStatuses(CookieSyncContext cookieSyncContext) {
        final BiddersContext biddersContext = cookieSyncContext.getBiddersContext();
        return biddersContext.rejectedBidders().entrySet().stream()
                .map(bidderWithReason ->
                        rejectionStatus(bidderWithReason.getKey(), bidderWithReason.getValue(), biddersContext))
                .filter(BidderUsersyncStatus::isError)
                .toList();
    }

    private BidderUsersyncStatus rejectionStatus(String bidder, RejectionReason reason, BiddersContext biddersContext) {
        final String cookieFamilyName = bidderCatalog.cookieFamilyName(bidder).orElse(bidder);
        BidderUsersyncStatus.BidderUsersyncStatusBuilder builder = BidderUsersyncStatus.builder()
                .bidder(cookieFamilyName);

        final boolean requested = biddersContext.isRequested(bidder);
        final boolean coopSync = biddersContext.isCoopSync(bidder);

        builder = switch (reason) {
            case INVALID_BIDDER -> builder.conditionalError(requested, "Unsupported bidder");
            case DISABLED_BIDDER -> builder.conditionalError(requested, "Disabled bidder");
            case REJECTED_BY_TCF -> builder.conditionalError(requested || coopSync, "Rejected by TCF");
            case REJECTED_BY_CCPA -> builder.conditionalError(requested || coopSync, "Rejected by CCPA");
            case DISALLOWED_ACTIVITY -> builder.conditionalError(requested || coopSync, "Disallowed activity");
            case UNCONFIGURED_USERSYNC -> builder.conditionalError(requested, "No sync config");
            case DISABLED_USERSYNC -> builder.conditionalError(requested || coopSync, "Sync disabled by config");
            case REJECTED_BY_FILTER -> builder.conditionalError(requested || coopSync, "Rejected by request filter");
            case ALREADY_IN_SYNC -> builder.conditionalError(requested, "Already in sync");
        };

        return builder.build();
    }

    private List<BidderUsersyncStatus> limitStatuses(Set<String> biddersToSync, CookieSyncContext cookieSyncContext) {
        final Set<String> droppedDueToLimitBidders = SetUtils.difference(
                cookieSyncContext.getBiddersContext().allowedRequestedBidders(), biddersToSync);

        return droppedDueToLimitBidders.stream()
                .map(bidder -> BidderUsersyncStatus.builder()
                        .bidder(bidderCatalog.cookieFamilyName(bidder).orElseThrow())
                        .error("limit reached")
                        .build())
                .toList();
    }

    private List<BidderUsersyncStatus> aliasSyncedAsRootStatuses(Set<String> biddersToSync,
                                                                 CookieSyncContext cookieSyncContext) {

        final Set<String> allowedRequestedBidders = cookieSyncContext.getBiddersContext().allowedRequestedBidders();

        return biddersToSync.stream()
                .filter(bidder -> allowedRequestedBidders.contains(bidder))
                .filter(this::isAliasSyncedAsRootFamily)
                .map(this::warningForAliasSyncedAsRootFamily)
                .toList();
    }

    private boolean isAliasSyncedAsRootFamily(String bidder) {
        return bidderCatalog.isAlias(bidder)
                && bidderCatalog.usersyncerByName(bidder)
                .map(Usersyncer::getCookieFamilySource)
                .filter(source -> source == CookieFamilySource.ROOT)
                .isPresent();
    }

    private BidderUsersyncStatus warningForAliasSyncedAsRootFamily(String bidder) {
        final String cookieFamilyName = bidderCatalog.cookieFamilyName(bidder).orElseThrow();
        return BidderUsersyncStatus.builder()
                .bidder(bidder)
                .error("synced as " + cookieFamilyName)
                .build();
    }

    private void updateCookieSyncTcfMetrics(BiddersContext biddersContext) {
        biddersContext.rejectedBidders().entrySet().stream()
                .filter(entry -> entry.getValue() == RejectionReason.REJECTED_BY_TCF)
                .map(Map.Entry::getKey)
                .forEach(bidder -> metrics.updateCookieSyncTcfBlockedMetric(
                        bidderCatalog.isValidName(bidder) ? bidder : "unknown"));
    }

    private static <T> T rethrowAsCookieSyncException(Throwable error, TcfContext tcfContext) {
        throw new CookieSyncException(error, tcfContext);
    }
}
