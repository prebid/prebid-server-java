package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;
import com.iab.openrtb.request.Imp;
import org.prebid.server.bidder.BidderCatalog;
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

    private static final String METRICS_UNKNOWN_BIDDER = "UNKNOWN";

    private final AccountMetricsVerbosity accountMetricsVerbosity;
    private final BidderCatalog bidderCatalog;

    private final Function<MetricName, RequestStatusMetrics> requestMetricsCreator;
    private final Function<String, AccountMetrics> accountMetricsCreator;
    private final Function<String, AdapterTypeMetrics> adapterMetricsCreator;
    private final Function<Integer, BidderCardinalityMetrics> bidderCardinalityMetricsCreator;
    private final Function<MetricName, CircuitBreakerMetrics> circuitBreakerMetricsCreator;
    private final Function<MetricName, SettingsCacheMetrics> settingsCacheMetricsCreator;
    // not thread-safe maps are intentionally used here because it's harmless in this particular case - eventually
    // this all boils down to metrics lookup by underlying metric registry and that operation is guaranteed to be
    // thread-safe
    private final Map<MetricName, RequestStatusMetrics> requestMetrics;
    private final Map<String, AccountMetrics> accountMetrics;
    private final Map<String, AdapterTypeMetrics> adapterMetrics;
    private final Map<Integer, BidderCardinalityMetrics> bidderCardinailtyMetrics;
    private final UserSyncMetrics userSyncMetrics;
    private final CookieSyncMetrics cookieSyncMetrics;
    private final PrivacyMetrics privacyMetrics;
    private final Map<MetricName, CircuitBreakerMetrics> circuitBreakerMetrics;
    private final CacheMetrics cacheMetrics;
    private final TimeoutNotificationMetrics timeoutNotificationMetrics;
    private final CurrencyRatesMetrics currencyRatesMetrics;
    private final Map<MetricName, SettingsCacheMetrics> settingsCacheMetrics;

    public Metrics(MetricRegistry metricRegistry, CounterType counterType, AccountMetricsVerbosity
            accountMetricsVerbosity, BidderCatalog bidderCatalog) {
        super(metricRegistry, counterType, MetricName::toString);

        this.accountMetricsVerbosity = Objects.requireNonNull(accountMetricsVerbosity);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);

        requestMetricsCreator = requestType -> new RequestStatusMetrics(metricRegistry, counterType, requestType);
        accountMetricsCreator = account -> new AccountMetrics(metricRegistry, counterType, account);
        adapterMetricsCreator = adapterType -> new AdapterTypeMetrics(metricRegistry, counterType, adapterType);
        bidderCardinalityMetricsCreator = cardinality -> new BidderCardinalityMetrics(
                metricRegistry, counterType, cardinality);
        circuitBreakerMetricsCreator = type -> new CircuitBreakerMetrics(metricRegistry, counterType, type);
        settingsCacheMetricsCreator = type -> new SettingsCacheMetrics(metricRegistry, counterType, type);
        requestMetrics = new EnumMap<>(MetricName.class);
        accountMetrics = new HashMap<>();
        adapterMetrics = new HashMap<>();
        bidderCardinailtyMetrics = new HashMap<>();
        userSyncMetrics = new UserSyncMetrics(metricRegistry, counterType);
        cookieSyncMetrics = new CookieSyncMetrics(metricRegistry, counterType);
        privacyMetrics = new PrivacyMetrics(metricRegistry, counterType);
        circuitBreakerMetrics = new HashMap<>();
        cacheMetrics = new CacheMetrics(metricRegistry, counterType);
        timeoutNotificationMetrics = new TimeoutNotificationMetrics(metricRegistry, counterType);
        currencyRatesMetrics = new CurrencyRatesMetrics(metricRegistry, counterType);
        settingsCacheMetrics = new HashMap<>();
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

    UserSyncMetrics userSync() {
        return userSyncMetrics;
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

    public void updateAppAndNoCookieAndImpsRequestedMetrics(boolean isApp, boolean liveUidsPresent, int numImps) {
        if (isApp) {
            incCounter(MetricName.app_requests);
        } else if (!liveUidsPresent) {
            incCounter(MetricName.no_cookie_requests);
        }
        incCounter(MetricName.imps_requested, numImps);
    }

    public void updateImpTypesMetrics(Map<String, Long> countPerMediaType) {
        for (Map.Entry<String, Long> mediaTypeCount : countPerMediaType.entrySet()) {
            switch (mediaTypeCount.getKey()) {
                case "banner":
                    incCounter(MetricName.imps_banner, mediaTypeCount.getValue());
                    break;
                case "video":
                    incCounter(MetricName.imps_video, mediaTypeCount.getValue());
                    break;
                case "native":
                    incCounter(MetricName.imps_native, mediaTypeCount.getValue());
                    break;
                case "audio":
                    incCounter(MetricName.imps_audio, mediaTypeCount.getValue());
                    break;
                default:
                    // ignore unrecognized media types
                    break;
            }
        }
    }

    public void updateImpTypesMetrics(List<Imp> imps) {

        final Map<String, Long> mediaTypeToCount = imps.stream()
                .map(Metrics::getPresentMediaTypes)
                .flatMap(Collection::stream)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        updateImpTypesMetrics(mediaTypeToCount);
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

    public void updateRequestTimeMetric(long millis) {
        updateTimer(MetricName.request_time, millis);
    }

    public void updateRequestTypeMetric(MetricName requestType, MetricName requestStatus) {
        forRequestType(requestType).incCounter(requestStatus);
    }

    public void updateRequestBidderCardinalityMetric(int bidderCardinality) {
        forBidderCardinality(bidderCardinality).incCounter(MetricName.requests);
    }

    public void updateAccountRequestMetrics(String accountId, MetricName requestType) {
        final AccountMetricsVerbosityLevel verbosityLevel = accountMetricsVerbosity.forAccount(accountId);
        if (verbosityLevel.isAtLeast(AccountMetricsVerbosityLevel.basic)) {
            final AccountMetrics accountMetrics = forAccount(accountId);

            accountMetrics.incCounter(MetricName.requests);
            if (verbosityLevel.isAtLeast(AccountMetricsVerbosityLevel.detailed)) {
                accountMetrics.requestType(requestType).incCounter(MetricName.requests);
            }
        }
    }

    public void updateAccountRequestRejectedMetrics(String accountId) {
        final AccountMetrics accountMetrics = forAccount(accountId);
        accountMetrics.requests().incCounter(MetricName.rejected);
    }

    public void updateAdapterRequestTypeAndNoCookieMetrics(String bidder, MetricName requestType, boolean noCookie) {
        final AdapterTypeMetrics adapterTypeMetrics = forAdapter(resolveMetricsBidderName(bidder));

        adapterTypeMetrics.requestType(requestType).incCounter(MetricName.requests);

        if (noCookie) {
            adapterTypeMetrics.incCounter(MetricName.no_cookie_requests);
        }
    }

    public void updateAdapterResponseTime(String bidder, String accountId, int responseTime) {
        final String metricsBidderName = resolveMetricsBidderName(bidder);
        final AdapterTypeMetrics adapterTypeMetrics = forAdapter(metricsBidderName);
        adapterTypeMetrics.updateTimer(MetricName.request_time, responseTime);

        if (accountMetricsVerbosity.forAccount(accountId).isAtLeast(AccountMetricsVerbosityLevel.detailed)) {
            final AdapterTypeMetrics accountAdapterMetrics =
                    forAccount(accountId).adapter().forAdapter(metricsBidderName);
            accountAdapterMetrics.updateTimer(MetricName.request_time, responseTime);
        }
    }

    public void updateAdapterRequestNobidMetrics(String bidder, String accountId) {
        final String metricsBidderName = resolveMetricsBidderName(bidder);
        forAdapter(metricsBidderName).request().incCounter(MetricName.nobid);
        if (accountMetricsVerbosity.forAccount(accountId).isAtLeast(AccountMetricsVerbosityLevel.detailed)) {
            forAccount(accountId).adapter().forAdapter(metricsBidderName).request().incCounter(MetricName.nobid);
        }
    }

    public void updateAdapterRequestGotbidsMetrics(String bidder, String accountId) {
        final String metricsBidderName = resolveMetricsBidderName(bidder);
        forAdapter(metricsBidderName).request().incCounter(MetricName.gotbids);
        if (accountMetricsVerbosity.forAccount(accountId).isAtLeast(AccountMetricsVerbosityLevel.detailed)) {
            forAccount(accountId).adapter().forAdapter(metricsBidderName).request().incCounter(MetricName.gotbids);
        }
    }

    public void updateAdapterBidMetrics(String bidder, String accountId, long cpm, boolean isAdm, String bidType) {
        final String metricsBidderName = resolveMetricsBidderName(bidder);
        final AdapterTypeMetrics adapterTypeMetrics = forAdapter(metricsBidderName);
        adapterTypeMetrics.updateHistogram(MetricName.prices, cpm);
        adapterTypeMetrics.incCounter(MetricName.bids_received);
        adapterTypeMetrics.forBidType(bidType)
                .incCounter(isAdm ? MetricName.adm_bids_received : MetricName.nurl_bids_received);

        if (accountMetricsVerbosity.forAccount(accountId).isAtLeast(AccountMetricsVerbosityLevel.detailed)) {
            final AdapterTypeMetrics accountAdapterMetrics =
                    forAccount(accountId).adapter().forAdapter(metricsBidderName);
            accountAdapterMetrics.updateHistogram(MetricName.prices, cpm);
            accountAdapterMetrics.incCounter(MetricName.bids_received);
        }
    }

    public void updateAdapterRequestErrorMetric(String bidder, MetricName errorMetric) {
        forAdapter(resolveMetricsBidderName(bidder)).request().incCounter(errorMetric);
    }

    public void updateSizeValidationMetrics(String bidder, String accountId, MetricName type) {
        forAdapter(resolveMetricsBidderName(bidder)).response().validation().size().incCounter(type);
        forAccount(accountId).response().validation().size().incCounter(type);
    }

    public void updateSecureValidationMetrics(String bidder, String accountId, MetricName type) {
        forAdapter(resolveMetricsBidderName(bidder)).response().validation().secure().incCounter(type);
        forAccount(accountId).response().validation().secure().incCounter(type);
    }

    public void updateUserSyncOptoutMetric() {
        userSync().incCounter(MetricName.opt_outs);
    }

    public void updateUserSyncBadRequestMetric() {
        userSync().incCounter(MetricName.bad_requests);
    }

    public void updateUserSyncSetsMetric(String bidder) {
        userSync().forBidder(bidder).incCounter(MetricName.sets);
    }

    public void updateUserSyncTcfBlockedMetric(String bidder) {
        userSync().forBidder(bidder).tcf().incCounter(MetricName.blocked);
    }

    public void updateCookieSyncRequestMetric() {
        incCounter(MetricName.cookie_sync_requests);
    }

    public void updateCookieSyncGenMetric(String bidder) {
        cookieSync().forBidder(bidder).incCounter(MetricName.gen);
    }

    public void updateCookieSyncMatchesMetric(String bidder) {
        cookieSync().forBidder(bidder).incCounter(MetricName.matches);
    }

    public void updateCookieSyncTcfBlockedMetric(String bidder) {
        cookieSync().forBidder(resolveMetricsBidderName(bidder)).tcf().incCounter(MetricName.blocked);
    }

    public void updateAuctionTcfMetrics(String bidder,
                                        MetricName requestType,
                                        boolean userIdRemoved,
                                        boolean geoMasked,
                                        boolean analyticsBlocked,
                                        boolean requestBlocked) {

        final TcfMetrics tcf = forAdapter(resolveMetricsBidderName(bidder)).requestType(requestType).tcf();

        if (userIdRemoved) {
            tcf.incCounter(MetricName.userid_removed);
        }
        if (geoMasked) {
            tcf.incCounter(MetricName.geo_masked);
        }
        if (analyticsBlocked) {
            tcf.incCounter(MetricName.analytics_blocked);
        }
        if (requestBlocked) {
            tcf.incCounter(MetricName.request_blocked);
        }
    }

    public void updatePrivacyCoppaMetric() {
        privacy().incCounter(MetricName.coppa);
    }

    public void updatePrivacyLmtMetric() {
        privacy().incCounter(MetricName.lmt);
    }

    public void updatePrivacyCcpaMetrics(boolean isSpecified, boolean isEnforced) {
        if (isSpecified) {
            privacy().usp().incCounter(MetricName.specified);
        }
        if (isEnforced) {
            privacy().usp().incCounter(MetricName.opt_out);
        }
    }

    public void updatePrivacyTcfMissingMetric() {
        privacy().tcf().incCounter(MetricName.missing);
    }

    public void updatePrivacyTcfInvalidMetric() {
        privacy().tcf().incCounter(MetricName.invalid);
    }

    public void updatePrivacyTcfRequestsMetric(int version) {
        final UpdatableMetrics versionMetrics = version == 2 ? privacy().tcf().v2() : privacy().tcf().v1();
        versionMetrics.incCounter(MetricName.requests);
    }

    public void updatePrivacyTcfGeoMetric(int version, Boolean inEea) {
        final UpdatableMetrics versionMetrics = version == 2 ? privacy().tcf().v2() : privacy().tcf().v1();

        final MetricName metricName = inEea == null
                ? MetricName.unknown_geo
                : inEea ? MetricName.in_geo : MetricName.out_geo;

        versionMetrics.incCounter(metricName);
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
        final TcfMetrics tcfMetrics = privacy().tcf();
        final TcfMetrics.TcfVersionMetrics tcfVersionMetrics = version == 2 ? tcfMetrics.v2() : tcfMetrics.v1();
        tcfVersionMetrics.vendorList().incCounter(metricName);
    }

    public void updateConnectionAcceptErrors() {
        incCounter(MetricName.connection_accept_errors);
    }

    public void updateDatabaseQueryTimeMetric(long millis) {
        updateTimer(MetricName.db_query_time, millis);
    }

    public void createDatabaseCircuitBreakerGauge(BooleanSupplier stateSupplier) {
        forCircuitBreakerType(MetricName.db)
                .createGauge(MetricName.opened, () -> stateSupplier.getAsBoolean() ? 1 : 0);
    }

    public void createHttpClientCircuitBreakerGauge(String name, BooleanSupplier stateSupplier) {
        forCircuitBreakerType(MetricName.http)
                .forName(name)
                .createGauge(MetricName.opened, () -> stateSupplier.getAsBoolean() ? 1 : 0);
    }

    public void removeHttpClientCircuitBreakerGauge(String name) {
        forCircuitBreakerType(MetricName.http).forName(name).removeMetric(MetricName.opened);
    }

    public void createHttpClientCircuitBreakerNumberGauge(LongSupplier numberSupplier) {
        forCircuitBreakerType(MetricName.http).createGauge(MetricName.existing, numberSupplier);
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
        forCircuitBreakerType(MetricName.geo)
                .createGauge(MetricName.opened, () -> stateSupplier.getAsBoolean() ? 1 : 0);
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
        cache().requests().updateTimer(MetricName.ok, timeElapsed);
        forAccount(accountId).cache().requests().updateTimer(MetricName.ok, timeElapsed);
    }

    public void updateCacheRequestFailedTime(String accountId, long timeElapsed) {
        cache().requests().updateTimer(MetricName.err, timeElapsed);
        forAccount(accountId).cache().requests().updateTimer(MetricName.err, timeElapsed);
    }

    public void updateCacheCreativeSize(String accountId, int creativeSize) {
        cache().updateHistogram(MetricName.creative_size, creativeSize);
        forAccount(accountId).cache().updateHistogram(MetricName.creative_size, creativeSize);
    }

    public void updateTimeoutNotificationMetric(boolean success) {
        if (success) {
            timeoutNotificationMetrics.incCounter(MetricName.ok);
        } else {
            timeoutNotificationMetrics.incCounter(MetricName.failed);
        }
    }

    public void createCurrencyRatesGauge(BooleanSupplier stateSupplier) {
        currencyRates().createGauge(MetricName.stale, () -> stateSupplier.getAsBoolean() ? 1 : 0);
    }

    public void updateSettingsCacheRefreshTime(MetricName cacheType, MetricName refreshType, long timeElapsed) {
        forSettingsCacheType(cacheType).forRefreshType(refreshType).updateTimer(MetricName.db_query_time, timeElapsed);
    }

    public void updateSettingsCacheRefreshErrorMetric(MetricName cacheType, MetricName refreshType) {
        forSettingsCacheType(cacheType).forRefreshType(refreshType).incCounter(MetricName.err);
    }

    public void updateSettingsCacheEventMetric(MetricName cacheType, MetricName event) {
        forSettingsCacheType(cacheType).incCounter(event);
    }

    private String resolveMetricsBidderName(String bidder) {
        return bidderCatalog.isValidName(bidder) ? bidder : METRICS_UNKNOWN_BIDDER;
    }
}
