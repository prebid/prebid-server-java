package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;
import com.iab.openrtb.request.Imp;
import org.prebid.server.hooks.execution.model.ExecutionAction;
import org.prebid.server.hooks.execution.model.ExecutionStatus;
import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.metric.model.AccountMetricsVerbosityLevel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;

/**
 * Defines interface for submitting different kinds of metrics.
 */
public class Metrics extends UpdatableMetrics {

    private static final String ALL_REQUEST_BIDDERS = "all";

    private final AccountMetricsVerbosity accountMetricsVerbosity;

    private final Function<MetricName, RequestStatusMetrics> requestMetricsCreator;
    private final Function<String, AccountMetrics> accountMetricsCreator;
    private final Function<String, AdapterTypeMetrics> adapterMetricsCreator;
    private final Function<String, AnalyticsReporterMetrics> analyticMetricsCreator;
    private final Function<Integer, BidderCardinalityMetrics> bidderCardinalityMetricsCreator;
    private final Function<MetricName, CircuitBreakerMetrics> circuitBreakerMetricsCreator;
    private final Function<MetricName, SettingsCacheMetrics> settingsCacheMetricsCreator;
    // not thread-safe maps are intentionally used here because it's harmless in this particular case - eventually
    // this all boils down to metrics lookup by underlying metric registry and that operation is guaranteed to be
    // thread-safe
    private final Map<MetricName, RequestStatusMetrics> requestMetrics;
    private final Map<String, AccountMetrics> accountMetrics;
    private final Map<String, AdapterTypeMetrics> adapterMetrics;
    private final Map<String, AnalyticsReporterMetrics> analyticMetrics;
    private final Map<Integer, BidderCardinalityMetrics> bidderCardinailtyMetrics;
    private final UserSyncMetrics userSyncMetrics;
    private final CookieSyncMetrics cookieSyncMetrics;
    private final PrivacyMetrics privacyMetrics;
    private final Map<MetricName, CircuitBreakerMetrics> circuitBreakerMetrics;
    private final CacheMetrics cacheMetrics;
    private final TimeoutNotificationMetrics timeoutNotificationMetrics;
    private final CurrencyRatesMetrics currencyRatesMetrics;
    private final Map<MetricName, SettingsCacheMetrics> settingsCacheMetrics;
    private final HooksMetrics hooksMetrics;
    private final PgMetrics pgMetrics;

    public Metrics(MetricRegistry metricRegistry, CounterType counterType,
                   AccountMetricsVerbosity accountMetricsVerbosity) {
        super(metricRegistry, counterType, MetricName::toString);

        this.accountMetricsVerbosity = Objects.requireNonNull(accountMetricsVerbosity);

        requestMetricsCreator = requestType -> new RequestStatusMetrics(metricRegistry, counterType, requestType);
        accountMetricsCreator = account -> new AccountMetrics(metricRegistry, counterType, account);
        adapterMetricsCreator = adapterType -> new AdapterTypeMetrics(metricRegistry, counterType, adapterType);
        bidderCardinalityMetricsCreator = cardinality -> new BidderCardinalityMetrics(
                metricRegistry, counterType, cardinality);
        analyticMetricsCreator = analyticCode -> new AnalyticsReporterMetrics(
                metricRegistry, counterType, analyticCode);
        circuitBreakerMetricsCreator = type -> new CircuitBreakerMetrics(metricRegistry, counterType, type);
        settingsCacheMetricsCreator = type -> new SettingsCacheMetrics(metricRegistry, counterType, type);
        requestMetrics = new EnumMap<>(MetricName.class);
        accountMetrics = new HashMap<>();
        adapterMetrics = new HashMap<>();
        analyticMetrics = new HashMap<>();
        bidderCardinailtyMetrics = new HashMap<>();
        userSyncMetrics = new UserSyncMetrics(metricRegistry, counterType);
        cookieSyncMetrics = new CookieSyncMetrics(metricRegistry, counterType);
        privacyMetrics = new PrivacyMetrics(metricRegistry, counterType);
        circuitBreakerMetrics = new HashMap<>();
        cacheMetrics = new CacheMetrics(metricRegistry, counterType);
        timeoutNotificationMetrics = new TimeoutNotificationMetrics(metricRegistry, counterType);
        currencyRatesMetrics = new CurrencyRatesMetrics(metricRegistry, counterType);
        settingsCacheMetrics = new HashMap<>();
        hooksMetrics = new HooksMetrics(metricRegistry, counterType);
        pgMetrics = new PgMetrics(metricRegistry, counterType);
    }

    RequestStatusMetrics forRequestType(MetricName requestType) {
        return requestMetrics.computeIfAbsent(requestType, requestMetricsCreator);
    }

    BidderCardinalityMetrics forBidderCardinality(int cardinality) {
        return bidderCardinailtyMetrics.computeIfAbsent(cardinality, bidderCardinalityMetricsCreator);
    }

    AccountMetrics forAccount(String account) {
        return accountMetrics.computeIfAbsent(account, accountMetricsCreator);
    }

    AdapterTypeMetrics forAdapter(String adapterType) {
        return adapterMetrics.computeIfAbsent(adapterType, adapterMetricsCreator);
    }

    AnalyticsReporterMetrics forAnalyticReporter(String analyticCode) {
        return analyticMetrics.computeIfAbsent(analyticCode, analyticMetricsCreator);
    }

    UserSyncMetrics userSync() {
        return userSyncMetrics;
    }

    PgMetrics pgMetrics() {
        return pgMetrics;
    }

    CookieSyncMetrics cookieSync() {
        return cookieSyncMetrics;
    }

    PrivacyMetrics privacy() {
        return privacyMetrics;
    }

    CircuitBreakerMetrics forCircuitBreakerType(MetricName type) {
        return circuitBreakerMetrics.computeIfAbsent(type, circuitBreakerMetricsCreator);
    }

    CacheMetrics cache() {
        return cacheMetrics;
    }

    CurrencyRatesMetrics currencyRates() {
        return currencyRatesMetrics;
    }

    SettingsCacheMetrics forSettingsCacheType(MetricName type) {
        return settingsCacheMetrics.computeIfAbsent(type, settingsCacheMetricsCreator);
    }

    HooksMetrics hooks() {
        return hooksMetrics;
    }

    public void updateAppAndNoCookieAndImpsRequestedMetrics(boolean isApp, boolean liveUidsPresent, int numImps) {
        if (isApp) {
            incCounter(MetricName.APP_REQUESTS);
        } else if (!liveUidsPresent) {
            incCounter(MetricName.NO_COOKIE_REQUESTS);
        }
        incCounter(MetricName.IMPS_REQUESTED, numImps);
    }

    public void updateImpTypesMetrics(List<Imp> imps) {

        final Map<String, Long> mediaTypeToCount = imps.stream()
                .map(Metrics::getPresentMediaTypes)
                .flatMap(Collection::stream)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        updateImpTypesMetrics(mediaTypeToCount);
    }

    void updateImpTypesMetrics(Map<String, Long> countPerMediaType) {
        for (Map.Entry<String, Long> mediaTypeCount : countPerMediaType.entrySet()) {
            switch (mediaTypeCount.getKey()) {
                case "banner":
                    incCounter(MetricName.IMPS_BANNER, mediaTypeCount.getValue());
                    break;
                case "video":
                    incCounter(MetricName.IMPS_VIDEO, mediaTypeCount.getValue());
                    break;
                case "native":
                    incCounter(MetricName.IMPS_NATIVE, mediaTypeCount.getValue());
                    break;
                case "audio":
                    incCounter(MetricName.IMPS_AUDIO, mediaTypeCount.getValue());
                    break;
                default:
                    // ignore unrecognized media types
                    break;
            }
        }
    }

    private static List<String> getPresentMediaTypes(Imp imp) {
        final List<String> impMediaTypes = new ArrayList<>();

        if (imp.getBanner() != null) {
            impMediaTypes.add("banner");
        }
        if (imp.getVideo() != null) {
            impMediaTypes.add("video");
        }
        if (imp.getXNative() != null) {
            impMediaTypes.add("native");
        }
        if (imp.getAudio() != null) {
            impMediaTypes.add("audio");
        }

        return impMediaTypes;
    }

    public void updateRequestTimeMetric(MetricName requestType, long millis) {
        updateTimer(requestType, millis);
    }

    public void updateRequestTypeMetric(MetricName requestType, MetricName requestStatus) {
        forRequestType(requestType).incCounter(requestStatus);
    }

    public void updateRequestBidderCardinalityMetric(int bidderCardinality) {
        forBidderCardinality(bidderCardinality).incCounter(MetricName.REQUESTS);
    }

    public void updateAccountRequestMetrics(String accountId, MetricName requestType) {
        final AccountMetricsVerbosityLevel verbosityLevel = accountMetricsVerbosity.forAccount(accountId);
        if (verbosityLevel.isAtLeast(AccountMetricsVerbosityLevel.BASIC)) {
            final AccountMetrics accountMetrics = forAccount(accountId);

            accountMetrics.incCounter(MetricName.REQUESTS);
            if (verbosityLevel.isAtLeast(AccountMetricsVerbosityLevel.DETAILED)) {
                accountMetrics.requestType(requestType).incCounter(MetricName.REQUESTS);
            }
        }
    }

    public void updateAccountRequestRejectedMetrics(String accountId) {
        final AccountMetrics accountMetrics = forAccount(accountId);
        accountMetrics.requests().incCounter(MetricName.REJECTED);
    }

    public void updateAdapterRequestTypeAndNoCookieMetrics(String bidder, MetricName requestType, boolean noCookie) {
        final AdapterTypeMetrics adapterTypeMetrics = forAdapter(bidder);

        adapterTypeMetrics.requestType(requestType).incCounter(MetricName.REQUESTS);

        if (noCookie) {
            adapterTypeMetrics.incCounter(MetricName.NO_COOKIE_REQUESTS);
        }
    }

    public void updateAdapterResponseTime(String bidder, String accountId, int responseTime) {
        final AdapterTypeMetrics adapterTypeMetrics = forAdapter(bidder);
        adapterTypeMetrics.updateTimer(MetricName.REQUEST_TIME, responseTime);

        if (accountMetricsVerbosity.forAccount(accountId).isAtLeast(AccountMetricsVerbosityLevel.DETAILED)) {
            final AdapterTypeMetrics accountAdapterMetrics =
                    forAccount(accountId).adapter().forAdapter(bidder);
            accountAdapterMetrics.updateTimer(MetricName.REQUEST_TIME, responseTime);
        }
    }

    public void updateAdapterRequestNobidMetrics(String bidder, String accountId) {
        forAdapter(bidder).request().incCounter(MetricName.NOBID);
        if (accountMetricsVerbosity.forAccount(accountId).isAtLeast(AccountMetricsVerbosityLevel.DETAILED)) {
            forAccount(accountId).adapter().forAdapter(bidder).request().incCounter(MetricName.NOBID);
        }
    }

    public void updateAdapterRequestGotbidsMetrics(String bidder, String accountId) {
        forAdapter(bidder).request().incCounter(MetricName.GOTBIDS);
        if (accountMetricsVerbosity.forAccount(accountId).isAtLeast(AccountMetricsVerbosityLevel.DETAILED)) {
            forAccount(accountId).adapter().forAdapter(bidder).request().incCounter(MetricName.GOTBIDS);
        }
    }

    public void updateAdapterBidMetrics(String bidder, String accountId, long cpm, boolean isAdm, String bidType) {
        final AdapterTypeMetrics adapterTypeMetrics = forAdapter(bidder);
        adapterTypeMetrics.updateHistogram(MetricName.PRICES, cpm);
        adapterTypeMetrics.incCounter(MetricName.BIDS_RECEIVED);
        adapterTypeMetrics.forBidType(bidType)
                .incCounter(isAdm ? MetricName.ADM_BIDS_RECEIVED : MetricName.NURL_BIDS_RECEIVED);

        if (accountMetricsVerbosity.forAccount(accountId).isAtLeast(AccountMetricsVerbosityLevel.DETAILED)) {
            final AdapterTypeMetrics accountAdapterMetrics =
                    forAccount(accountId).adapter().forAdapter(bidder);
            accountAdapterMetrics.updateHistogram(MetricName.PRICES, cpm);
            accountAdapterMetrics.incCounter(MetricName.BIDS_RECEIVED);
        }
    }

    public void updateAdapterRequestErrorMetric(String bidder, MetricName errorMetric) {
        forAdapter(bidder).request().incCounter(errorMetric);
    }

    public void updateAnalyticEventMetric(String analyticCode, MetricName eventType, MetricName result) {
        forAnalyticReporter(analyticCode).forEventType(eventType).incCounter(result);
    }

    public void updateSizeValidationMetrics(String bidder, String accountId, MetricName type) {
        forAdapter(bidder).response().validation().size().incCounter(type);
        forAccount(accountId).response().validation().size().incCounter(type);
    }

    public void updateSecureValidationMetrics(String bidder, String accountId, MetricName type) {
        forAdapter(bidder).response().validation().secure().incCounter(type);
        forAccount(accountId).response().validation().secure().incCounter(type);
    }

    public void updateUserSyncOptoutMetric() {
        userSync().incCounter(MetricName.OPT_OUTS);
    }

    public void updateUserSyncBadRequestMetric() {
        userSync().incCounter(MetricName.BAD_REQUESTS);
    }

    public void updateUserSyncSetsMetric(String bidder) {
        userSync().forBidder(bidder).incCounter(MetricName.SETS);
    }

    public void updateUserSyncTcfBlockedMetric(String bidder) {
        userSync().forBidder(bidder).tcf().incCounter(MetricName.BLOCKED);
    }

    public void updateUserSyncTcfInvalidMetric(String bidder) {
        userSync().forBidder(bidder).tcf().incCounter(MetricName.INVALID);
    }

    public void updateUserSyncTcfInvalidMetric() {
        updateUserSyncTcfInvalidMetric(ALL_REQUEST_BIDDERS);
    }

    public void updateCookieSyncRequestMetric() {
        incCounter(MetricName.COOKIE_SYNC_REQUESTS);
    }

    public void updateCookieSyncGenMetric(String bidder) {
        cookieSync().forBidder(bidder).incCounter(MetricName.GEN);
    }

    public void updateCookieSyncMatchesMetric(String bidder) {
        cookieSync().forBidder(bidder).incCounter(MetricName.MATCHES);
    }

    public void updateCookieSyncTcfBlockedMetric(String bidder) {
        cookieSync().forBidder(bidder).tcf().incCounter(MetricName.BLOCKED);
    }

    public void updateAuctionTcfMetrics(String bidder,
                                        MetricName requestType,
                                        boolean userIdRemoved,
                                        boolean geoMasked,
                                        boolean analyticsBlocked,
                                        boolean requestBlocked) {

        final TcfMetrics tcf = forAdapter(bidder).requestType(requestType).tcf();

        if (userIdRemoved) {
            tcf.incCounter(MetricName.USERID_REMOVED);
        }
        if (geoMasked) {
            tcf.incCounter(MetricName.GEO_MASKED);
        }
        if (analyticsBlocked) {
            tcf.incCounter(MetricName.ANALYTICS_BLOCKED);
        }
        if (requestBlocked) {
            tcf.incCounter(MetricName.REQUEST_BLOCKED);
        }
    }

    public void updatePrivacyCoppaMetric() {
        privacy().incCounter(MetricName.COPPA);
    }

    public void updatePrivacyLmtMetric() {
        privacy().incCounter(MetricName.LMT);
    }

    public void updatePrivacyCcpaMetrics(boolean isSpecified, boolean isEnforced) {
        if (isSpecified) {
            privacy().usp().incCounter(MetricName.SPECIFIED);
        }
        if (isEnforced) {
            privacy().usp().incCounter(MetricName.OPT_OUT);
        }
    }

    public void updatePrivacyTcfMissingMetric() {
        privacy().tcf().incCounter(MetricName.MISSING);
    }

    public void updatePrivacyTcfInvalidMetric() {
        privacy().tcf().incCounter(MetricName.INVALID);
    }

    public void updatePrivacyTcfRequestsMetric(int version) {
        final UpdatableMetrics versionMetrics = privacy().tcf().fromVersion(version);
        versionMetrics.incCounter(MetricName.REQUESTS);
    }

    public void updatePrivacyTcfGeoMetric(int version, Boolean inEea) {
        final UpdatableMetrics versionMetrics = privacy().tcf().fromVersion(version);

        final MetricName metricName = inEea == null
                ? MetricName.UNKNOWN_GEO
                : inEea ? MetricName.IN_GEO : MetricName.OUT_GEO;

        versionMetrics.incCounter(metricName);
    }

    public void updatePrivacyTcfVendorListMissingMetric(int version) {
        updatePrivacyTcfVendorListMetric(version, MetricName.MISSING);
    }

    public void updatePrivacyTcfVendorListOkMetric(int version) {
        updatePrivacyTcfVendorListMetric(version, MetricName.OK);
    }

    public void updatePrivacyTcfVendorListErrorMetric(int version) {
        updatePrivacyTcfVendorListMetric(version, MetricName.ERR);
    }

    public void updatePrivacyTcfVendorListFallbackMetric(int version) {
        updatePrivacyTcfVendorListMetric(version, MetricName.FALLBACK);
    }

    private void updatePrivacyTcfVendorListMetric(int version, MetricName metricName) {
        final TcfMetrics tcfMetrics = privacy().tcf();
        tcfMetrics.fromVersion(version).vendorList().incCounter(metricName);
    }

    public void updateConnectionAcceptErrors() {
        incCounter(MetricName.CONNECTION_ACCEPT_ERRORS);
    }

    public void updateDatabaseQueryTimeMetric(long millis) {
        updateTimer(MetricName.DB_QUERY_TIME, millis);
    }

    public void createDatabaseCircuitBreakerGauge(BooleanSupplier stateSupplier) {
        forCircuitBreakerType(MetricName.DB)
                .createGauge(MetricName.OPENED, () -> stateSupplier.getAsBoolean() ? 1 : 0);
    }

    public void createHttpClientCircuitBreakerGauge(String name, BooleanSupplier stateSupplier) {
        forCircuitBreakerType(MetricName.HTTP)
                .forName(name)
                .createGauge(MetricName.OPENED, () -> stateSupplier.getAsBoolean() ? 1 : 0);
    }

    public void removeHttpClientCircuitBreakerGauge(String name) {
        forCircuitBreakerType(MetricName.HTTP).forName(name).removeMetric(MetricName.OPENED);
    }

    public void createHttpClientCircuitBreakerNumberGauge(LongSupplier numberSupplier) {
        forCircuitBreakerType(MetricName.HTTP).createGauge(MetricName.EXISTING, numberSupplier);
    }

    public void updatePlannerRequestMetric(boolean successful) {
        pgMetrics().incCounter(MetricName.PLANNER_REQUESTS);
        if (successful) {
            pgMetrics().incCounter(MetricName.PLANNER_REQUEST_SUCCESSFUL);
        } else {
            pgMetrics().incCounter(MetricName.PLANNER_REQUEST_FAILED);
        }
    }

    public void updateDeliveryRequestMetric(boolean successful) {
        pgMetrics().incCounter(MetricName.DELIVERY_REQUESTS);
        if (successful) {
            pgMetrics().incCounter(MetricName.DELIVERY_REQUEST_SUCCESSFUL);
        } else {
            pgMetrics().incCounter(MetricName.DELIVERY_REQUEST_FAILED);
        }
    }

    public void updateWinEventRequestMetric(boolean successful) {
        incCounter(MetricName.WIN_REQUESTS);
        if (successful) {
            incCounter(MetricName.WIN_REQUEST_SUCCESSFUL);
        } else {
            incCounter(MetricName.WIN_REQUEST_FAILED);
        }
    }

    public void updateUserDetailsRequestMetric(boolean successful) {
        incCounter(MetricName.USER_DETAILS_REQUESTS);
        if (successful) {
            incCounter(MetricName.USER_DETAILS_REQUEST_SUCCESSFUL);
        } else {
            incCounter(MetricName.USER_DETAILS_REQUEST_FAILED);
        }
    }

    public void updateWinRequestTime(long millis) {
        updateTimer(MetricName.WIN_REQUEST_TIME, millis);
    }

    public void updateLineItemsNumberMetric(long count) {
        pgMetrics().incCounter(MetricName.PLANNER_LINEITEMS_RECEIVED, count);
    }

    public void updatePlannerRequestTime(long millis) {
        pgMetrics().updateTimer(MetricName.PLANNER_REQUEST_TIME, millis);
    }

    public void updateDeliveryRequestTime(long millis) {
        pgMetrics().updateTimer(MetricName.DELIVERY_REQUEST_TIME, millis);
    }

    public void updateGeoLocationMetric(boolean successful) {
        incCounter(MetricName.GEOLOCATION_REQUESTS);
        if (successful) {
            incCounter(MetricName.GEOLOCATION_SUCCESSFUL);
        } else {
            incCounter(MetricName.GEOLOCATION_FAIL);
        }
    }

    public void createGeoLocationCircuitBreakerGauge(BooleanSupplier stateSupplier) {
        forCircuitBreakerType(MetricName.GEO)
                .createGauge(MetricName.OPENED, () -> stateSupplier.getAsBoolean() ? 1 : 0);
    }

    public void updateStoredRequestMetric(boolean found) {
        if (found) {
            incCounter(MetricName.STORED_REQUESTS_FOUND);
        } else {
            incCounter(MetricName.STORED_REQUESTS_MISSING);
        }
    }

    public void updateStoredImpsMetric(boolean found) {
        if (found) {
            incCounter(MetricName.STORED_IMPS_FOUND);
        } else {
            incCounter(MetricName.STORED_IMPS_MISSING);
        }
    }

    public void updateCacheRequestSuccessTime(String accountId, long timeElapsed) {
        cache().requests().updateTimer(MetricName.OK, timeElapsed);
        forAccount(accountId).cache().requests().updateTimer(MetricName.OK, timeElapsed);
    }

    public void updateCacheRequestFailedTime(String accountId, long timeElapsed) {
        cache().requests().updateTimer(MetricName.ERR, timeElapsed);
        forAccount(accountId).cache().requests().updateTimer(MetricName.ERR, timeElapsed);
    }

    public void updateCacheCreativeSize(String accountId, int creativeSize, MetricName creativeType) {
        cache().creativeSize().updateHistogram(creativeType, creativeSize);
        forAccount(accountId).cache().creativeSize().updateHistogram(creativeType, creativeSize);
    }

    public void updateTimeoutNotificationMetric(boolean success) {
        if (success) {
            timeoutNotificationMetrics.incCounter(MetricName.OK);
        } else {
            timeoutNotificationMetrics.incCounter(MetricName.FAILED);
        }
    }

    public void createCurrencyRatesGauge(BooleanSupplier stateSupplier) {
        currencyRates().createGauge(MetricName.STALE, () -> stateSupplier.getAsBoolean() ? 1 : 0);
    }

    public void updateSettingsCacheRefreshTime(MetricName cacheType, MetricName refreshType, long timeElapsed) {
        forSettingsCacheType(cacheType).forRefreshType(refreshType).updateTimer(MetricName.DB_QUERY_TIME, timeElapsed);
    }

    public void updateSettingsCacheRefreshErrorMetric(MetricName cacheType, MetricName refreshType) {
        forSettingsCacheType(cacheType).forRefreshType(refreshType).incCounter(MetricName.ERR);
    }

    public void updateSettingsCacheEventMetric(MetricName cacheType, MetricName event) {
        forSettingsCacheType(cacheType).incCounter(event);
    }

    public void updateHooksMetrics(
            String moduleCode,
            Stage stage,
            String hookImplCode,
            ExecutionStatus status,
            Long executionTime,
            ExecutionAction action) {

        final HookImplMetrics hookImplMetrics = hooks().module(moduleCode).stage(stage).hookImpl(hookImplCode);

        hookImplMetrics.incCounter(MetricName.CALL);
        if (status == ExecutionStatus.SUCCESS) {
            hookImplMetrics.success().incCounter(HookMetricMapper.fromAction(action));
        } else {
            hookImplMetrics.incCounter(HookMetricMapper.fromStatus(status));
        }
        hookImplMetrics.updateTimer(MetricName.DURATION, executionTime);
    }

    public void updateAccountHooksMetrics(
            String accountId,
            String moduleCode,
            ExecutionStatus status,
            ExecutionAction action) {

        if (accountMetricsVerbosity.forAccount(accountId).isAtLeast(AccountMetricsVerbosityLevel.DETAILED)) {
            final ModuleMetrics accountModuleMetrics = forAccount(accountId).hooks().module(moduleCode);

            accountModuleMetrics.incCounter(MetricName.CALL);
            if (status == ExecutionStatus.SUCCESS) {
                accountModuleMetrics.success().incCounter(HookMetricMapper.fromAction(action));
            } else {
                accountModuleMetrics.incCounter(MetricName.FAILURE);
            }
        }
    }

    public void updateAccountModuleDurationMetric(String accountId, String moduleCode, Long executionTime) {
        if (accountMetricsVerbosity.forAccount(accountId).isAtLeast(AccountMetricsVerbosityLevel.DETAILED)) {
            forAccount(accountId).hooks().module(moduleCode).updateTimer(MetricName.DURATION, executionTime);
        }
    }

    private static class HookMetricMapper {

        private static final EnumMap<ExecutionStatus, MetricName> STATUS_TO_METRIC =
                new EnumMap<>(ExecutionStatus.class);
        private static final EnumMap<ExecutionAction, MetricName> ACTION_TO_METRIC =
                new EnumMap<>(ExecutionAction.class);

        static {
            STATUS_TO_METRIC.put(ExecutionStatus.FAILURE, MetricName.FAILURE);
            STATUS_TO_METRIC.put(ExecutionStatus.TIMEOUT, MetricName.TIMEOUT);
            STATUS_TO_METRIC.put(ExecutionStatus.INVOCATION_FAILURE, MetricName.EXECUTION_ERROR);
            STATUS_TO_METRIC.put(ExecutionStatus.EXECUTION_FAILURE, MetricName.EXECUTION_ERROR);

            ACTION_TO_METRIC.put(ExecutionAction.NO_ACTION, MetricName.NOOP);
            ACTION_TO_METRIC.put(ExecutionAction.UPDATE, MetricName.UPDATE);
            ACTION_TO_METRIC.put(ExecutionAction.REJECT, MetricName.REJECT);
        }

        static MetricName fromStatus(ExecutionStatus status) {
            return STATUS_TO_METRIC.getOrDefault(status, MetricName.UNKNOWN);
        }

        static MetricName fromAction(ExecutionAction action) {
            return ACTION_TO_METRIC.getOrDefault(action, MetricName.UNKNOWN);
        }
    }

    public void updateWinNotificationMetric() {
        incCounter(MetricName.WIN_NOTIFICATIONS);
    }

    public void updateWinRequestPreparationFailed() {
        incCounter(MetricName.WIN_REQUEST_PREPARATION_FAILED);
    }

    public void updateUserDetailsRequestPreparationFailed() {
        incCounter(MetricName.USER_DETAILS_REQUEST_PREPARATION_FAILED);
    }
}
