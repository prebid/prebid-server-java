package org.prebid.server.metric;

import com.iab.openrtb.request.Imp;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import org.prebid.server.hooks.execution.model.ExecutionAction;
import org.prebid.server.hooks.execution.model.ExecutionStatus;
import org.prebid.server.hooks.execution.model.Stage;
import org.prebid.server.metric.model.AccountMetricsVerbosityLevel;
import org.prebid.server.settings.model.Account;

import java.util.ArrayList;
import java.util.Arrays;
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
public class Metrics extends UpdatableMetrics {

    private static final String ALL_REQUEST_BIDDERS = "all";

    private final AccountMetricsVerbosityResolver accountMetricsVerbosityResolver;

    public Metrics(CompositeMeterRegistry meterRegistry,
                   AccountMetricsVerbosityResolver accountMetricsVerbosityResolver) {
        super(meterRegistry, MetricName::toString);

        this.accountMetricsVerbosityResolver = Objects.requireNonNull(accountMetricsVerbosityResolver);
    }

    public void updateAppAndNoCookieAndImpsRequestedMetrics(boolean isApp, boolean liveUidsPresent, int numImps) {
        if (isApp) {
            incCounter(MetricName.app_requests);
        } else if (!liveUidsPresent) {
            incCounter(MetricName.no_cookie_requests);
        }

        incCounter(MetricName.imps_requested, numImps);
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
                case "banner" -> incCounter(MetricName.imps_banner, mediaTypeCount.getValue());
                case "video" -> incCounter(MetricName.imps_video, mediaTypeCount.getValue());
                case "native" -> incCounter(MetricName.imps_native, mediaTypeCount.getValue());
                case "audio" -> incCounter(MetricName.imps_audio, mediaTypeCount.getValue());
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
        updateTimer(requestType, millis);
    }

    public void updateRequestTypeMetric(MetricName requestType, MetricName requestStatus) {
        List<MetricTag> metricTags = Arrays.asList(
            new MetricTag("request_type", requestType.toString()),
            new MetricTag("request_status", requestStatus.toString())
        );
        incCounter(MetricName.requests, metricTags);
    }

    public void updateRequestBidderCardinalityMetric(int bidderCardinality) {
        incCounter(MetricName.bidder_cardinality_requests, "cardinality", String.valueOf(bidderCardinality));
    }

    public void updateAccountRequestMetrics(Account account, MetricName requestType) {
        final AccountMetricsVerbosityLevel verbosityLevel = accountMetricsVerbosityResolver.forAccount(account);
        if (verbosityLevel.isAtLeast(AccountMetricsVerbosityLevel.basic)) {
            List<MetricTag> metricTags = Arrays.asList(
                new MetricTag("account", account.getId())
            );

            if (verbosityLevel.isAtLeast(AccountMetricsVerbosityLevel.detailed)) {
                metricTags.add(new MetricTag("request_type", requestType.toString()));
            }

            incCounter(MetricName.requests, metricTags);
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
        List<MetricTag> metricTags = Arrays.asList(
            new MetricTag("account", accountId),
            new MetricTag("rejected_reason", metricName.toString())
        );
        incCounter(MetricName.requests, metricTags);
    }

    public void updateAdapterRequestTypeAndNoCookieMetrics(String bidder, MetricName requestType, boolean noCookie) {
        List<MetricTag> metricTags = Arrays.asList(
            new MetricTag("adapter", bidder)
        );

        incCounter(MetricName.requests, metricTags, "request_type", requestType.toString());

        if (noCookie) {
            incCounter(MetricName.no_cookie_requests, metricTags);
        }
    }

    public void updateAdapterResponseTime(String bidder, Account account, int responseTime) {
        List<MetricTag> metricTags = Arrays.asList(
            new MetricTag("adapter", bidder)
        );

        if (accountMetricsVerbosityResolver.forAccount(account).isAtLeast(AccountMetricsVerbosityLevel.detailed)) {
            metricTags.add(new MetricTag("account", account.getId()));
        }

        updateTimer(MetricName.request_time, responseTime, metricTags);
    }

    public void updateAdapterRequestNobidMetrics(String bidder, Account account) {
        List<MetricTag> metricTags = Arrays.asList(
            new MetricTag("adapter", bidder),
            new MetricTag("result", MetricName.nobid.toString())
        );

        if (accountMetricsVerbosityResolver.forAccount(account).isAtLeast(AccountMetricsVerbosityLevel.detailed)) {
            metricTags.add(new MetricTag("account", account.getId()));
        }

        incCounter(MetricName.adapter_requests_result, metricTags);
    }

    public void updateAdapterRequestGotbidsMetrics(String bidder, Account account) {
        List<MetricTag> metricTags = Arrays.asList(
            new MetricTag("adapter", bidder),
            new MetricTag("result", MetricName.gotbids.toString())
        );

        if (accountMetricsVerbosityResolver.forAccount(account).isAtLeast(AccountMetricsVerbosityLevel.detailed)) {
            metricTags.add(new MetricTag("account", account.getId()));
        }

        incCounter(MetricName.adapter_requests_result, metricTags);
    }

    public void updateAdapterBidMetrics(String bidder, Account account, long cpm, boolean isAdm, String bidType) {
        List<MetricTag> metricTags = Arrays.asList(
            new MetricTag("adapter", bidder),
            new MetricTag("bid_type",
                isAdm ? MetricName.adm_bids_received.toString() : MetricName.nurl_bids_received.toString())
        );

        if (accountMetricsVerbosityResolver.forAccount(account).isAtLeast(AccountMetricsVerbosityLevel.detailed)) {
            metricTags.add(new MetricTag("account", account.getId()));
        }

        updateHistogram(MetricName.prices, cpm, metricTags);
        incCounter(MetricName.bids_received, metricTags);
    }

    public void updateAdapterRequestErrorMetric(String bidder, MetricName errorMetric) {
        List<MetricTag> metricTags = Arrays.asList(
            new MetricTag("adapter", bidder),
            new MetricTag("result", errorMetric.toString())
        );
        incCounter(MetricName.adapter_requests_result, metricTags);
    }

    public void updateAnalyticEventMetric(String analyticCode, MetricName eventType, MetricName result) {
        List<MetricTag> metricTags = Arrays.asList(
            new MetricTag("reporter_name", analyticCode),
            new MetricTag("event_type", eventType.toString()),
            new MetricTag("result", result.toString())
        );
        incCounter(MetricName.analytics_events, metricTags);
    }

    public void updatePriceFloorFetchMetric() {
        incCounter(MetricName.price_floors_fetch_failure);
    }

    public void updatePriceFloorGeneralAlertsMetric() {
        incCounter(MetricName.price_floors_general_err);
    }

    public void updateAlertsConfigFailed(String accountId, MetricName metricName) {
        List<MetricTag> metricTags = Arrays.asList(
            new MetricTag("account", accountId),
            new MetricTag("type", metricName.toString())
        );
        incCounter(MetricName.alerts_account_config, metricTags);
    }

    public void updateSizeValidationMetrics(String bidder, String accountId, MetricName type) {
        List<MetricTag> metricTags = Arrays.asList(
            new MetricTag("adapter", bidder),
            new MetricTag("account", accountId),
            new MetricTag("validation", "size"),
            new MetricTag("level", type.toString())
        );
        incCounter(MetricName.response_validation, metricTags);
    }

    public void updateSecureValidationMetrics(String bidder, String accountId, MetricName type) {
        List<MetricTag> metricTags = Arrays.asList(
            new MetricTag("adapter", bidder),
            new MetricTag("account", accountId),
            new MetricTag("validation", "secure"),
            new MetricTag("level", type.toString())
        );
        incCounter(MetricName.response_validation, metricTags);
    }

    public void updateUserSyncOptoutMetric() {
        incCounter(MetricName.user_sync_opt_outs);
    }

    public void updateUserSyncBadRequestMetric() {
        incCounter(MetricName.user_sync_bad_requests);
    }

    public void updateUserSyncSetsMetric(String bidder) {
        List<MetricTag> metricTags = Arrays.asList(
            new MetricTag("adapter", bidder),
            new MetricTag("action", MetricName.sets.toString())
        );
        incCounter(MetricName.adapter_user_sync_action, metricTags);
    }

    public void updateUserSyncTcfBlockedMetric(String bidder) {
        List<MetricTag> metricTags = Arrays.asList(
            new MetricTag("adapter", bidder),
            new MetricTag("action", MetricName.blocked.toString())
        );
        incCounter(MetricName.adapter_user_sync_tcf, metricTags);
    }

    public void updateUserSyncTcfInvalidMetric(String bidder) {
        List<MetricTag> metricTags = Arrays.asList(
            new MetricTag("adapter", bidder),
            new MetricTag("action", MetricName.invalid.toString())
        );
        incCounter(MetricName.adapter_user_sync_tcf, metricTags);
    }

    public void updateUserSyncTcfInvalidMetric() {
        updateUserSyncTcfInvalidMetric(ALL_REQUEST_BIDDERS);
    }

    public void updateCookieSyncRequestMetric() {
        incCounter(MetricName.cookie_sync_requests);
    }

    public void updateCookieSyncGenMetric(String bidder) {
        List<MetricTag> metricTags = Arrays.asList(
            new MetricTag("adapter", bidder),
            new MetricTag("action", MetricName.gen.toString())
        );
        incCounter(MetricName.adapter_cookie_sync_action, metricTags);
    }

    public void updateCookieSyncMatchesMetric(String bidder) {
        List<MetricTag> metricTags = Arrays.asList(
            new MetricTag("adapter", bidder),
            new MetricTag("action", MetricName.matches.toString())
        );
        incCounter(MetricName.adapter_cookie_sync_action, metricTags);
    }

    public void updateCookieSyncTcfBlockedMetric(String bidder) {
        List<MetricTag> metricTags = Arrays.asList(
            new MetricTag("adapter", bidder),
            new MetricTag("action", MetricName.blocked.toString())
        );
        incCounter(MetricName.adapter_cookie_sync_tcf, metricTags);
    }

    public void updateAuctionTcfMetrics(String bidder,
                                        MetricName requestType,
                                        boolean userIdRemoved,
                                        boolean geoMasked,
                                        boolean analyticsBlocked,
                                        boolean requestBlocked) {

        List<MetricTag> metricTags = Arrays.asList(
            new MetricTag("adapter", bidder),
            new MetricTag("type", requestType.toString())
        );

        if (userIdRemoved) {
            metricTags.add(new MetricTag("tcf", MetricName.userid_removed.toString()));
            incCounter(MetricName.adapter_tcf, metricTags);
        }
        if (geoMasked) {
            metricTags.add(new MetricTag("tcf", MetricName.geo_masked.toString()));
            incCounter(MetricName.adapter_tcf, metricTags);
        }
        if (analyticsBlocked) {
            metricTags.add(new MetricTag("tcf", MetricName.analytics_blocked.toString()));
            incCounter(MetricName.adapter_tcf, metricTags);
        }
        if (requestBlocked) {
            metricTags.add(new MetricTag("tcf", MetricName.request_blocked.toString()));
            incCounter(MetricName.adapter_tcf, metricTags);
        }
    }

    public void updatePrivacyCoppaMetric() {
        incCounter(MetricName.privacy_coopa);
    }

    public void updatePrivacyLmtMetric() {
        incCounter(MetricName.privacy_lmt);
    }

    public void updatePrivacyCcpaMetrics(boolean isSpecified, boolean isEnforced) {
        if (isSpecified) {
            incCounter(MetricName.privacy_usp_specified);
        }
        if (isEnforced) {
            incCounter(MetricName.privacy_usp_opt_out);
        }
    }

    public void updatePrivacyTcfMissingMetric() {
        incCounter(MetricName.privacy_tcf_errors, new MetricTag("error", MetricName.missing.toString()));
    }

    public void updatePrivacyTcfInvalidMetric() {
        incCounter(MetricName.privacy_tcf_errors, new MetricTag("error", MetricName.invalid.toString()));
    }

    public void updatePrivacyTcfRequestsMetric(int version) {
        incCounter(MetricName.privacy_tcf_requests, new MetricTag("tcf", String.valueOf(version)));
    }

    public void updatePrivacyTcfGeoMetric(int version, Boolean inEea) {
        final MetricName metricName = inEea == null
                ? MetricName.privacy_tcf_unknown_geo
                : inEea ? MetricName.privacy_tcf_in_geo : MetricName.privacy_tcf_out_geo;

        incCounter(metricName, new MetricTag("tcf", String.valueOf(version)));
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
        List<MetricTag> metricTags = Arrays.asList(
            new MetricTag("tcf", String.valueOf(version)),
            new MetricTag("status", metricName.toString())
        );
        incCounter(MetricName.privacy_tcf_vendorlist, metricTags);
    }

    public void updateConnectionAcceptErrors() {
        incCounter(MetricName.connection_accept_errors);
    }

    public void updateDatabaseQueryTimeMetric(long millis) {
        updateTimer(MetricName.db_query_time, millis);
    }

    public void createDatabaseCircuitBreakerGauge(BooleanSupplier stateSupplier) {
        createGauge(MetricName.cb_db_open, () -> stateSupplier.getAsBoolean() ? 1 : 0);
    }

    public void createHttpClientCircuitBreakerGauge(String name, BooleanSupplier stateSupplier) {
        createGauge(MetricName.cb_http_open,
                () -> stateSupplier.getAsBoolean() ? 1 : 0, new MetricTag("name", name));
    }

    public void removeHttpClientCircuitBreakerGauge(String name) {
        removeMetric(MetricName.cb_http_open, new MetricTag("name", name));
    }

    public void createHttpClientCircuitBreakerNumberGauge(LongSupplier numberSupplier) {
        createGauge(MetricName.cb_http_existing, numberSupplier);
    }

    public void updatePlannerRequestMetric(boolean successful) {
        incCounter(MetricName.pg_planner_requests);
        if (successful) {
            incCounter(MetricName.pg_planner_request_successful);
        } else {
            incCounter(MetricName.pg_planner_request_failed);
        }
    }

    public void updateDeliveryRequestMetric(boolean successful) {
        incCounter(MetricName.pg_delivery_requests);
        if (successful) {
            incCounter(MetricName.pg_delivery_request_successful);
        } else {
            incCounter(MetricName.pg_delivery_request_failed);
        }
    }

    public void updateWinEventRequestMetric(boolean successful) {
        incCounter(MetricName.win_requests);
        if (successful) {
            incCounter(MetricName.win_request_successful);
        } else {
            incCounter(MetricName.win_request_failed);
        }
    }

    public void updateUserDetailsRequestMetric(boolean successful) {
        incCounter(MetricName.user_details_requests);
        if (successful) {
            incCounter(MetricName.user_details_request_successful);
        } else {
            incCounter(MetricName.user_details_request_failed);
        }
    }

    public void updateWinRequestTime(long millis) {
        updateTimer(MetricName.win_request_time, millis);
    }

    public void updateLineItemsNumberMetric(long count) {
        incCounter(MetricName.pg_planner_lineitems_received, count);
    }

    public void updatePlannerRequestTime(long millis) {
        updateTimer(MetricName.pg_planner_request_time, millis);
    }

    public void updateDeliveryRequestTime(long millis) {
        updateTimer(MetricName.pg_delivery_request_time, millis);
    }

    public void updateGeoLocationMetric(boolean successful) {
        incCounter(MetricName.geolocation_requests);
        if (successful) {
            incCounter(MetricName.geolocation_successful);
        } else {
            incCounter(MetricName.geolocation_fail);
        }
    }

    public void createGeoLocationCircuitBreakerGauge(BooleanSupplier stateSupplier) {
        createGauge(MetricName.cb_geo_open, () -> stateSupplier.getAsBoolean() ? 1 : 0);
    }

    public void updateStoredRequestMetric(boolean found) {
        if (found) {
            incCounter(MetricName.stored_requests_found);
        } else {
            incCounter(MetricName.stored_requests_missing);
        }
    }

    public void updateStoredImpsMetric(boolean found) {
        if (found) {
            incCounter(MetricName.stored_imps_found);
        } else {
            incCounter(MetricName.stored_imps_missing);
        }
    }

    public void updateCacheRequestSuccessTime(String accountId, long timeElapsed) {
        List<MetricTag> metricTags = Arrays.asList(
            new MetricTag("account", accountId),
            new MetricTag("result", "ok")
        );
        updateTimer(MetricName.prebid_cache_requests, timeElapsed, metricTags);
    }

    public void updateCacheRequestFailedTime(String accountId, long timeElapsed) {
        List<MetricTag> metricTags = Arrays.asList(
            new MetricTag("account", accountId),
            new MetricTag("result", "err")
        );
        updateTimer(MetricName.prebid_cache_requests, timeElapsed, metricTags);
    }

    public void updateCacheCreativeSize(String accountId, int creativeSize, MetricName creativeType) {
        List<MetricTag> metricTags = Arrays.asList(
            new MetricTag("account", accountId),
            new MetricTag("creative_type", creativeType.toString())
        );
        updateHistogram(MetricName.prebid_cache_creative_size, creativeSize, metricTags);
    }

    public void updateTimeoutNotificationMetric(boolean success) {
        MetricTag metricTag = new MetricTag("status", "failed");

        if (success) {
            metricTag = new MetricTag("status", "ok");
        }

        incCounter(MetricName.timeout_notifications, metricTag);
    }

    public void createCurrencyRatesGauge(BooleanSupplier stateSupplier) {
        createGauge(MetricName.currency_rates_stale, () -> stateSupplier.getAsBoolean() ? 1 : 0);
    }

    public void updateSettingsCacheRefreshTime(MetricName cacheType, MetricName refreshType, long timeElapsed) {
        List<MetricTag> metricTags = Arrays.asList(
            new MetricTag("cache_type", cacheType.toString()),
            new MetricTag("refresh_type", refreshType.toString())
        );
        updateTimer(MetricName.settings_cache_refresh_db_query_time, timeElapsed, metricTags);
    }

    public void updateSettingsCacheRefreshErrorMetric(MetricName cacheType, MetricName refreshType) {
        List<MetricTag> metricTags = Arrays.asList(
            new MetricTag("cache_type", cacheType.toString()),
            new MetricTag("refresh_type", refreshType.toString())
        );
        incCounter(MetricName.settings_cache_refresh_err, metricTags);
    }

    public void updateSettingsCacheEventMetric(MetricName cacheType, MetricName event) {
        List<MetricTag> metricTags = Arrays.asList(
            new MetricTag("cache_type", cacheType.toString()),
            new MetricTag("event", event.toString())
        );
        incCounter(MetricName.settings_cache_account, metricTags);
    }

    public void updateHooksMetrics(
            String moduleCode,
            Stage stage,
            String hookImplCode,
            ExecutionStatus status,
            Long executionTime,
            ExecutionAction action) {

        List<MetricTag> metricTags = Arrays.asList(
            new MetricTag("module", moduleCode),
            new MetricTag("stage", stage.toString()),
            new MetricTag("hook", hookImplCode),
            new MetricTag("action", HookMetricMapper.fromAction(action).toString()));

        if (status == ExecutionStatus.success) {
            incCounter(MetricName.module_calls, metricTags, "status", "success");
        } else {
            incCounter(MetricName.module_calls, metricTags, "status", "failure");
        }
        updateTimer(MetricName.module_calls, executionTime);
    }

    public void updateAccountHooksMetrics(
            Account account,
            String moduleCode,
            ExecutionStatus status,
            ExecutionAction action) {

        if (accountMetricsVerbosityResolver.forAccount(account).isAtLeast(AccountMetricsVerbosityLevel.detailed)) {
            List<MetricTag> metricTags = Arrays.asList(
                new MetricTag("account", account.getId()),
                new MetricTag("module", moduleCode),
                new MetricTag("action", HookMetricMapper.fromAction(action).toString()));

            if (status == ExecutionStatus.success) {
                incCounter(MetricName.account_module_calls, metricTags, "status", "success");
            } else {
                incCounter(MetricName.account_module_calls, metricTags, "status", "failure");
            }
        }
    }

    public void updateAccountModuleDurationMetric(Account account, String moduleCode, Long executionTime) {
        if (accountMetricsVerbosityResolver.forAccount(account).isAtLeast(AccountMetricsVerbosityLevel.detailed)) {
            List<MetricTag> metricTags = Arrays.asList(
                new MetricTag("account", account.getId()),
                new MetricTag("module", moduleCode));
            updateTimer(MetricName.account_module_calls, executionTime, metricTags);
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
        incCounter(MetricName.win_notifications);
    }

    public void updateWinRequestPreparationFailed() {
        incCounter(MetricName.win_request_preparation_failed);
    }

    public void updateUserDetailsRequestPreparationFailed() {
        incCounter(MetricName.user_details_request_preparation_failed);
    }
}
