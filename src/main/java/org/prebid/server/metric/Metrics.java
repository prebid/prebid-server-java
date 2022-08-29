package org.prebid.server.metric;

import com.iab.openrtb.request.Imp;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import org.prebid.server.hooks.execution.model.ExecutionAction;
import org.prebid.server.hooks.execution.model.ExecutionStatus;
import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.metric.model.AccountMetricsVerbosityLevel;
import org.prebid.server.settings.model.Account;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
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
public class Metrics {

    private static final String ALL_REQUEST_BIDDERS = "all";

    private final AccountMetricsVerbosityResolver accountMetricsVerbosityResolver;
    private final CompositeMeterRegistry meterRegistry;

    public Metrics(CompositeMeterRegistry meterRegistry,
                   AccountMetricsVerbosityResolver accountMetricsVerbosityResolver) {
        this.accountMetricsVerbosityResolver = Objects.requireNonNull(accountMetricsVerbosityResolver);
        this.meterRegistry = Objects.requireNonNull(meterRegistry);
    }

    public Metric newMetric(MetricName metricName) {
        return new Metric(metricName, meterRegistry);
    }

    public void updateAppAndNoCookieAndImpsRequestedMetrics(boolean isApp, boolean liveUidsPresent, int numImps) {
        if (isApp) {
            newMetric(MetricName.app_requests).incCounter();
        } else if (!liveUidsPresent) {
            newMetric(MetricName.no_cookie_requests).incCounter();
        }

        newMetric(MetricName.imps_requested).incCounter(numImps);
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
                case "banner" -> newMetric(MetricName.imps_banner).incCounter(mediaTypeCount.getValue());
                case "video" -> newMetric(MetricName.imps_video).incCounter(mediaTypeCount.getValue());
                case "native" -> newMetric(MetricName.imps_native).incCounter(mediaTypeCount.getValue());
                case "audio" -> newMetric(MetricName.imps_audio).incCounter(mediaTypeCount.getValue());
                // ignore unrecognized media types
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
        newMetric(requestType).updateTimer(millis);
    }

    public void updateRequestTypeMetric(MetricName requestType, MetricName requestStatus) {
        newMetric(MetricName.requests)
                .withTag("request_type", requestType.toString())
                .withTag("request_status", requestStatus.toString())
                .incCounter();
    }

    public void updateRequestBidderCardinalityMetric(int bidderCardinality) {
        newMetric(MetricName.bidder_cardinality_requests)
                .withTag("cardinality", String.valueOf(bidderCardinality))
                .incCounter();
    }

    public void updateAccountRequestMetrics(Account account, MetricName requestType) {
        final AccountMetricsVerbosityLevel verbosityLevel = accountMetricsVerbosityResolver.forAccount(account);
        if (verbosityLevel.isAtLeast(AccountMetricsVerbosityLevel.basic)) {
            Metric metric = newMetric(MetricName.requests)
                    .withTag("account", account.getId());

            if (verbosityLevel.isAtLeast(AccountMetricsVerbosityLevel.detailed)) {
                metric.withTag("request_type", requestType.toString());
            }

            metric.incCounter();
        }
    }

    public void updateAccountRequestRejectedByInvalidAccountMetrics(String accountId) {
        updateAccountRequestsMetrics(accountId, MetricName.rejected_by_invalid_account);
    }

    public void updateAccountRequestRejectedByInvalidStoredImpMetrics(String accountId) {
        updateAccountRequestsMetrics(accountId, MetricName.rejected_by_invalid_stored_impr);
    }

    public void updateAccountRequestRejectedByInvalidStoredRequestMetrics(String accountId) {
        updateAccountRequestsMetrics(accountId, MetricName.rejected_by_invalid_stored_request);
    }

    private void updateAccountRequestsMetrics(String accountId, MetricName metricName) {
        newMetric(MetricName.requests)
                .withTag("account", accountId)
                .withTag("rejected_reason", metricName.toString())
                .incCounter();
    }

    public void updateAdapterRequestTypeAndNoCookieMetrics(String bidder, MetricName requestType, boolean noCookie) {
        newMetric(MetricName.requests)
                .withTag("adapter", bidder)
                .withTag("request_type", requestType.toString())
                .incCounter();

        if (noCookie) {
            newMetric(MetricName.no_cookie_requests).withTag("adapter", bidder).incCounter();
        }
    }

    public void updateAdapterResponseTime(String bidder, Account account, int responseTime) {
        Metric metric = newMetric(MetricName.request_time).withTag("adapter", bidder);

        if (accountMetricsVerbosityResolver.forAccount(account).isAtLeast(AccountMetricsVerbosityLevel.detailed)) {
            metric.withTag("account", account.getId());
        }

        metric.updateTimer(responseTime);
    }

    public void updateAdapterRequestNobidMetrics(String bidder, Account account) {
        Metric metric = newMetric(MetricName.adapter_requests_result)
                .withTag("adapter", bidder)
                .withTag("result", MetricName.nobid.toString());

        if (accountMetricsVerbosityResolver.forAccount(account).isAtLeast(AccountMetricsVerbosityLevel.detailed)) {
            metric.withTag("account", account.getId());
        }

        metric.incCounter();
    }

    public void updateAdapterRequestGotbidsMetrics(String bidder, Account account) {
        Metric metric = newMetric(MetricName.adapter_requests_result)
                .withTag("adapter", bidder)
                .withTag("result", MetricName.gotbids.toString());

        if (accountMetricsVerbosityResolver.forAccount(account).isAtLeast(AccountMetricsVerbosityLevel.detailed)) {
            metric.withTag("account", account.getId());
        }

        metric.incCounter();
    }

    public void updateAdapterBidMetrics(String bidder, Account account, long cpm, boolean isAdm, String bidType) {
        Metric pricesMetric = newMetric(MetricName.prices)
                .withTag("adapter", bidder);

        Metric bidsDeliveredMetric = newMetric(MetricName.bids_received)
                .withTag("adapter", bidder)
                .withTag("bid_type",
                    isAdm ? MetricName.adm_bids_received.toString() : MetricName.nurl_bids_received.toString());

        if (accountMetricsVerbosityResolver.forAccount(account).isAtLeast(AccountMetricsVerbosityLevel.detailed)) {
            pricesMetric = pricesMetric.withTag("account", account.getId());
            bidsDeliveredMetric = bidsDeliveredMetric.withTag("account", account.getId());
        }

        pricesMetric.updateHistogram(cpm);
        bidsDeliveredMetric.incCounter();
    }

    public void updateAdapterRequestErrorMetric(String bidder, MetricName errorMetric) {
        newMetric(MetricName.adapter_requests_result)
                .withTag("adapter", bidder)
                .withTag("result", errorMetric.toString())
                .incCounter();
    }

    public void updateAnalyticEventMetric(String analyticCode, MetricName eventType, MetricName result) {
        newMetric(MetricName.analytics_events)
                .withTag("reporter_name", analyticCode)
                .withTag("event_type", eventType.toString())
                .withTag("result", result.toString())
                .incCounter();
    }

    public void updatePriceFloorFetchMetric() {
        newMetric(MetricName.price_floors_fetch_failure).incCounter();
    }

    public void updatePriceFloorGeneralAlertsMetric() {
        newMetric(MetricName.price_floors_general_err).incCounter();
    }

    public void updateAlertsConfigFailed(String accountId, MetricName metricName) {
        newMetric(MetricName.alerts_account_config)
                .withTag("account", accountId)
                .withTag("type", metricName.toString())
                .incCounter();
    }

    public void updateSizeValidationMetrics(String bidder, String accountId, MetricName type) {
        newMetric(MetricName.response_validation)
                .withTag("adapter", bidder)
                .withTag("account", accountId)
                .withTag("validation", "size")
                .withTag("level", type.toString())
                .incCounter();
    }

    public void updateSecureValidationMetrics(String bidder, String accountId, MetricName type) {
        newMetric(MetricName.response_validation)
                .withTag("adapter", bidder)
                .withTag("account", accountId)
                .withTag("validation", "secure")
                .withTag("level", type.toString())
                .incCounter();
    }

    public void updateUserSyncOptoutMetric() {
        newMetric(MetricName.user_sync_opt_outs).incCounter();
    }

    public void updateUserSyncBadRequestMetric() {
        newMetric(MetricName.user_sync_bad_requests).incCounter();
    }

    public void updateUserSyncSetsMetric(String bidder) {
        newMetric(MetricName.adapter_user_sync_action)
                .withTag("adapter", bidder)
                .withTag("action", MetricName.sets.toString())
                .incCounter();
    }

    public void updateUserSyncTcfBlockedMetric(String bidder) {
        newMetric(MetricName.adapter_user_sync_tcf)
                .withTag("adapter", bidder)
                .withTag("action", MetricName.blocked.toString())
                .incCounter();
    }

    public void updateUserSyncTcfInvalidMetric(String bidder) {
        newMetric(MetricName.adapter_user_sync_tcf)
                .withTag("adapter", bidder)
                .withTag("action", MetricName.invalid.toString())
                .incCounter();
    }

    public void updateUserSyncTcfInvalidMetric() {
        updateUserSyncTcfInvalidMetric(ALL_REQUEST_BIDDERS);
    }

    public void updateCookieSyncRequestMetric() {
        newMetric(MetricName.cookie_sync_requests).incCounter();
    }

    public void updateCookieSyncGenMetric(String bidder) {
        newMetric(MetricName.adapter_cookie_sync_action)
                .withTag("adapter", bidder)
                .withTag("action", MetricName.gen.toString())
                .incCounter();
    }

    public void updateCookieSyncMatchesMetric(String bidder) {
        newMetric(MetricName.adapter_cookie_sync_action)
                .withTag("adapter", bidder)
                .withTag("action", MetricName.matches.toString())
                .incCounter();
    }

    public void updateCookieSyncTcfBlockedMetric(String bidder) {
        newMetric(MetricName.adapter_cookie_sync_tcf)
                .withTag("adapter", bidder)
                .withTag("action", MetricName.blocked.toString())
                .incCounter();
    }

    public void updateAuctionTcfMetrics(String bidder,
                                        MetricName requestType,
                                        boolean userIdRemoved,
                                        boolean geoMasked,
                                        boolean analyticsBlocked,
                                        boolean requestBlocked) {

        Metric metric = newMetric(MetricName.adapter_tcf)
                .withTag("adapter", bidder)
                .withTag("type", requestType.toString());

        if (userIdRemoved) {
            metric.withTag("tcf", MetricName.userid_removed.toString())
                    .incCounter();
        }
        if (geoMasked) {
            metric.withTag("tcf", MetricName.geo_masked.toString())
                    .incCounter();
        }
        if (analyticsBlocked) {
            metric.withTag("tcf", MetricName.analytics_blocked.toString())
                    .incCounter();
        }
        if (requestBlocked) {
            metric.withTag("tcf", MetricName.request_blocked.toString())
                    .incCounter();
        }
    }

    public void updatePrivacyCoppaMetric() {
        newMetric(MetricName.privacy_coppa).incCounter();
    }

    public void updatePrivacyLmtMetric() {
        newMetric(MetricName.privacy_lmt).incCounter();
    }

    public void updatePrivacyCcpaMetrics(boolean isSpecified, boolean isEnforced) {
        if (isSpecified) {
            newMetric(MetricName.privacy_usp_specified).incCounter();
        }
        if (isEnforced) {
            newMetric(MetricName.privacy_usp_opt_out).incCounter();
        }
    }

    public void updatePrivacyTcfMissingMetric() {
        newMetric(MetricName.privacy_tcf_errors)
                .withTag("error", MetricName.missing.toString())
                .incCounter();
    }

    public void updatePrivacyTcfInvalidMetric() {
        newMetric(MetricName.privacy_tcf_errors)
                .withTag("error", MetricName.invalid.toString())
                .incCounter();
    }

    public void updatePrivacyTcfRequestsMetric(int version) {
        newMetric(MetricName.privacy_tcf_requests)
                .withTag("tcf", String.valueOf(version))
                .incCounter();
    }

    public void updatePrivacyTcfGeoMetric(int version, Boolean inEea) {
        final MetricName metricName = inEea == null
                ? MetricName.privacy_tcf_unknown_geo
                : inEea ? MetricName.privacy_tcf_in_geo : MetricName.privacy_tcf_out_geo;

        newMetric(metricName)
                .withTag("tcf", String.valueOf(version))
                .incCounter();
    }

    public void updatePrivacyTcfVendorListMissingMetric(int version) {
        updatePrivacyTcfVendorListMetric(version, MetricName.missing);
    }

    public void updatePrivacyTcfVendorListOkMetric(int version) {
        updatePrivacyTcfVendorListMetric(version, MetricName.ok);
    }

    public void updatePrivacyTcfVendorListErrorMetric(int version) {
        updatePrivacyTcfVendorListMetric(version, MetricName.err);
    }

    public void updatePrivacyTcfVendorListFallbackMetric(int version) {
        updatePrivacyTcfVendorListMetric(version, MetricName.fallback);
    }

    private void updatePrivacyTcfVendorListMetric(int version, MetricName metricName) {
        newMetric(MetricName.privacy_tcf_vendorlist)
                .withTag("tcf", String.valueOf(version))
                .withTag("status", metricName.toString())
                .incCounter();
    }

    public void updateConnectionAcceptErrors() {
        newMetric(MetricName.connection_accept_errors).incCounter();
    }

    public void updateDatabaseQueryTimeMetric(long millis) {
        newMetric(MetricName.db_query_time).updateTimer(millis);
    }

    public void createDatabaseCircuitBreakerGauge(BooleanSupplier stateSupplier) {
        newMetric(MetricName.cb_db_open)
                .createGauge(() -> stateSupplier.getAsBoolean() ? 1 : 0);
    }

    public void createHttpClientCircuitBreakerGauge(String name, BooleanSupplier stateSupplier) {
        newMetric(MetricName.cb_http_open).withTag("name", name)
            .createGauge(() -> stateSupplier.getAsBoolean() ? 1 : 0);
    }

    public void removeHttpClientCircuitBreakerGauge(String name) {
        newMetric(MetricName.cb_http_open).withTag("name", name).removeMetric();
    }

    public void createHttpClientCircuitBreakerNumberGauge(LongSupplier numberSupplier) {
        newMetric(MetricName.cb_http_existing).createGauge(numberSupplier);
    }

    public void updatePlannerRequestMetric(boolean successful) {
        newMetric(MetricName.pg_planner_requests).incCounter();
        if (successful) {
            newMetric(MetricName.pg_planner_request_successful).incCounter();
        } else {
            newMetric(MetricName.pg_planner_request_failed).incCounter();
        }
    }

    public void updateDeliveryRequestMetric(boolean successful) {
        newMetric(MetricName.pg_delivery_requests).incCounter();
        if (successful) {
            newMetric(MetricName.pg_delivery_request_successful).incCounter();
        } else {
            newMetric(MetricName.pg_delivery_request_failed).incCounter();
        }
    }

    public void updateWinEventRequestMetric(boolean successful) {
        newMetric(MetricName.win_requests).incCounter();
        if (successful) {
            newMetric(MetricName.win_request_successful).incCounter();
        } else {
            newMetric(MetricName.win_request_failed).incCounter();
        }
    }

    public void updateUserDetailsRequestMetric(boolean successful) {
        newMetric(MetricName.user_details_requests).incCounter();
        if (successful) {
            newMetric(MetricName.user_details_request_successful).incCounter();
        } else {
            newMetric(MetricName.user_details_request_failed).incCounter();
        }
    }

    public void updateWinRequestTime(long millis) {
        newMetric(MetricName.win_request_time).updateTimer(millis);
    }

    public void updateLineItemsNumberMetric(long count) {
        newMetric(MetricName.pg_planner_lineitems_received).incCounter(count);
    }

    public void updatePlannerRequestTime(long millis) {
        newMetric(MetricName.pg_planner_request_time).updateTimer(millis);
    }

    public void updateDeliveryRequestTime(long millis) {
        newMetric(MetricName.pg_delivery_request_time).updateTimer(millis);
    }

    public void updateGeoLocationMetric(boolean successful) {
        newMetric(MetricName.geolocation_requests).incCounter();
        if (successful) {
            newMetric(MetricName.geolocation_successful).incCounter();
        } else {
            newMetric(MetricName.geolocation_fail).incCounter();
        }
    }

    public void createGeoLocationCircuitBreakerGauge(BooleanSupplier stateSupplier) {
        newMetric(MetricName.cb_geo_open).createGauge(() -> stateSupplier.getAsBoolean() ? 1 : 0);
    }

    public void updateStoredRequestMetric(boolean found) {
        if (found) {
            newMetric(MetricName.stored_requests_found).incCounter();
        } else {
            newMetric(MetricName.stored_requests_missing).incCounter();
        }
    }

    public void updateStoredImpsMetric(boolean found) {
        if (found) {
            newMetric(MetricName.stored_imps_found).incCounter();
        } else {
            newMetric(MetricName.stored_imps_missing).incCounter();
        }
    }

    public void updateCacheRequestSuccessTime(String accountId, long timeElapsed) {
        newMetric(MetricName.prebid_cache_requests)
                .withTag("account", accountId)
                .withTag("result", "ok")
                .updateTimer(timeElapsed);
    }

    public void updateCacheRequestFailedTime(String accountId, long timeElapsed) {
        newMetric(MetricName.prebid_cache_requests)
                .withTag("account", accountId)
                .withTag("result", "err")
                .updateTimer(timeElapsed);
    }

    public void updateCacheCreativeSize(String accountId, int creativeSize, MetricName creativeType) {
        newMetric(MetricName.prebid_cache_creative_size)
                .withTag("account", accountId)
                .withTag("creative_type", creativeType.toString())
                .updateHistogram(creativeSize);
    }

    public void updateTimeoutNotificationMetric(boolean success) {
        if (success) {
            newMetric(MetricName.timeout_notifications).withTag("status", "ok")
                    .incCounter();
        } else {
            newMetric(MetricName.timeout_notifications).withTag("status", "failed")
                    .incCounter();
        }
    }

    public void createCurrencyRatesGauge(BooleanSupplier stateSupplier) {
        newMetric(MetricName.currency_rates_stale)
                .createGauge(() -> stateSupplier.getAsBoolean() ? 1 : 0);
    }

    public void updateSettingsCacheRefreshTime(MetricName cacheType, MetricName refreshType, long timeElapsed) {
        newMetric(MetricName.settings_cache_refresh_db_query_time)
                .withTag("cache_type", cacheType.toString())
                .withTag("refresh_type", refreshType.toString())
                .updateTimer(timeElapsed);
    }

    public void updateSettingsCacheRefreshErrorMetric(MetricName cacheType, MetricName refreshType) {
        newMetric(MetricName.settings_cache_refresh_err)
                .withTag("cache_type", cacheType.toString())
                .withTag("refresh_type", refreshType.toString())
                .incCounter();
    }

    public void updateSettingsCacheEventMetric(MetricName cacheType, MetricName event) {
        newMetric(MetricName.settings_cache_account)
                .withTag("cache_type", cacheType.toString())
                .withTag("event", event.toString())
                .incCounter();
    }

    public void updateHooksMetrics(
            String moduleCode,
            Stage stage,
            String hookImplCode,
            ExecutionStatus status,
            Long executionTime,
            ExecutionAction action) {

        Metric metric = newMetric(MetricName.module_calls)
                .withTag("module", moduleCode)
                .withTag("stage", stage.toString())
                .withTag("hook", hookImplCode)
                .withTag("action", HookMetricMapper.fromAction(action).toString());

        if (status == ExecutionStatus.success) {
            metric.withTag("status", "success").incCounter();
        } else {
            metric.withTag("status", "failure").incCounter();
        }

        metric.updateTimer(executionTime);
    }

    public void updateAccountHooksMetrics(
            Account account,
            String moduleCode,
            ExecutionStatus status,
            ExecutionAction action) {

        if (accountMetricsVerbosityResolver.forAccount(account).isAtLeast(AccountMetricsVerbosityLevel.detailed)) {
            Metric metric = newMetric(MetricName.account_module_calls)
                    .withTag("module", moduleCode)
                    .withTag("account", account.getId())
                    .withTag("action", HookMetricMapper.fromAction(action).toString());

            if (status == ExecutionStatus.success) {
                metric.withTag("status", "success").incCounter();
            } else {
                metric.withTag("status", "failure").incCounter();
            }
        }
    }

    public void updateAccountModuleDurationMetric(Account account, String moduleCode, Long executionTime) {
        if (accountMetricsVerbosityResolver.forAccount(account).isAtLeast(AccountMetricsVerbosityLevel.detailed)) {
            newMetric(MetricName.account_module_calls)
                    .withTag("module", moduleCode)
                    .withTag("account", account.getId())
                    .updateTimer(executionTime);
        }
    }

    private static class HookMetricMapper {

        private static final EnumMap<ExecutionStatus, MetricName> STATUS_TO_METRIC =
                new EnumMap<>(ExecutionStatus.class);
        private static final EnumMap<ExecutionAction, MetricName> ACTION_TO_METRIC =
                new EnumMap<>(ExecutionAction.class);

        static {
            STATUS_TO_METRIC.put(ExecutionStatus.failure, MetricName.failure);
            STATUS_TO_METRIC.put(ExecutionStatus.timeout, MetricName.timeout);
            STATUS_TO_METRIC.put(ExecutionStatus.invocation_failure, MetricName.execution_error);
            STATUS_TO_METRIC.put(ExecutionStatus.execution_failure, MetricName.execution_error);

            ACTION_TO_METRIC.put(ExecutionAction.no_action, MetricName.noop);
            ACTION_TO_METRIC.put(ExecutionAction.update, MetricName.update);
            ACTION_TO_METRIC.put(ExecutionAction.reject, MetricName.reject);
        }

        static MetricName fromStatus(ExecutionStatus status) {
            return STATUS_TO_METRIC.getOrDefault(status, MetricName.unknown);
        }

        static MetricName fromAction(ExecutionAction action) {
            return ACTION_TO_METRIC.getOrDefault(action, MetricName.unknown);
        }
    }

    public void updateWinNotificationMetric() {
        newMetric(MetricName.win_notifications).incCounter();
    }

    public void updateWinRequestPreparationFailed() {
        newMetric(MetricName.win_request_preparation_failed).incCounter();
    }

    public void updateUserDetailsRequestPreparationFailed() {
        newMetric(MetricName.user_details_request_preparation_failed).incCounter();
    }
}
