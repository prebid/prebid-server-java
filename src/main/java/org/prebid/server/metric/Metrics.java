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
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Defines interface for submitting different kinds of metrics.
 */
public class Metrics extends UpdatableMetrics {

    private static final String METRICS_UNKNOWN_BIDDER = "UNKNOWN";

    private AccountMetricsVerbosity accountMetricsVerbosity;
    private final BidderCatalog bidderCatalog;

    private final Function<MetricName, RequestStatusMetrics> requestMetricsCreator;
    private final Function<String, AccountMetrics> accountMetricsCreator;
    private final Function<String, AdapterMetrics> adapterMetricsCreator;
    // not thread-safe maps are intentionally used here because it's harmless in this particular case - eventually
    // this all boils down to metrics lookup by underlying metric registry and that operation is guaranteed to be
    // thread-safe
    private final Map<MetricName, RequestStatusMetrics> requestMetrics;
    private final Map<String, AccountMetrics> accountMetrics;
    private final Map<String, AdapterMetrics> adapterMetrics;
    private final UserSyncMetrics userSyncMetrics;
    private final CookieSyncMetrics cookieSyncMetrics;

    public Metrics(MetricRegistry metricRegistry, CounterType counterType, AccountMetricsVerbosity
            accountMetricsVerbosity, BidderCatalog bidderCatalog) {
        super(metricRegistry, counterType, MetricName::toString);

        this.accountMetricsVerbosity = Objects.requireNonNull(accountMetricsVerbosity);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);

        requestMetricsCreator = requestType -> new RequestStatusMetrics(metricRegistry, counterType, requestType);
        accountMetricsCreator = account -> new AccountMetrics(metricRegistry, counterType, account);
        adapterMetricsCreator = adapterType -> new AdapterMetrics(metricRegistry, counterType, adapterType);
        requestMetrics = new EnumMap<>(MetricName.class);
        accountMetrics = new HashMap<>();
        adapterMetrics = new HashMap<>();
        userSyncMetrics = new UserSyncMetrics(metricRegistry, counterType);
        cookieSyncMetrics = new CookieSyncMetrics(metricRegistry, counterType);
    }

    RequestStatusMetrics forRequestType(MetricName requestType) {
        return requestMetrics.computeIfAbsent(requestType, requestMetricsCreator);
    }

    AccountMetrics forAccount(String account) {
        return accountMetrics.computeIfAbsent(account, accountMetricsCreator);
    }

    AdapterMetrics forAdapter(String adapterType) {
        return adapterMetrics.computeIfAbsent(adapterType, adapterMetricsCreator);
    }

    UserSyncMetrics userSync() {
        return userSyncMetrics;
    }

    CookieSyncMetrics cookieSync() {
        return cookieSyncMetrics;
    }

    public void updateSafariRequestsMetric(boolean isSafari) {
        if (isSafari) {
            incCounter(MetricName.safari_requests);
        }
    }

    public void updateAppAndNoCookieAndImpsRequestedMetrics(boolean isApp, boolean liveUidsPresent, boolean isSafari,
                                                            int numImps) {
        if (isApp) {
            incCounter(MetricName.app_requests);
        } else if (!liveUidsPresent) {
            incCounter(MetricName.no_cookie_requests);
            if (isSafari) {
                incCounter(MetricName.safari_no_cookie_requests);
            }
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

    public void updateAccountRequestMetrics(String accountId, MetricName requestType) {
        final AccountMetricsVerbosityLevel verbosityLevel = accountMetricsVerbosity.forAccount(accountId);
        if (verbosityLevel.isAtLeast(AccountMetricsVerbosityLevel.basic)) {
            final AccountMetrics accountMetrics = forAccount(accountId);

            accountMetrics.incCounter(MetricName.requests);
            if (verbosityLevel.isAtLeast(AccountMetricsVerbosityLevel.detailed)) {
                accountMetrics.requestType().incCounter(requestType);
            }
        }
    }

    public void updateAdapterRequestTypeAndNoCookieMetrics(String bidder, MetricName requestType, boolean noCookie) {
        final AdapterMetrics adapterMetrics = forAdapter(resolveMetricsBidderName(bidder));

        adapterMetrics.requestType().incCounter(requestType);

        if (noCookie) {
            adapterMetrics.incCounter(MetricName.no_cookie_requests);
        }
    }

    private String resolveMetricsBidderName(String bidder) {
        if (bidderCatalog.isValidName(bidder)) {
            return bidder;
        }
        final String nameByAlias = bidderCatalog.nameByAlias(bidder);
        return nameByAlias != null ? nameByAlias : METRICS_UNKNOWN_BIDDER;
    }

    public void updateAdapterResponseTime(String bidder, String accountId, int responseTime) {
        final String metricsBidderName = resolveMetricsBidderName(bidder);
        final AdapterMetrics adapterMetrics = forAdapter(metricsBidderName);
        adapterMetrics.updateTimer(MetricName.request_time, responseTime);

        if (accountMetricsVerbosity.forAccount(accountId).isAtLeast(AccountMetricsVerbosityLevel.detailed)) {
            final AdapterMetrics accountAdapterMetrics = forAccount(accountId).forAdapter(metricsBidderName);
            accountAdapterMetrics.updateTimer(MetricName.request_time, responseTime);
        }
    }

    public void updateAdapterRequestNobidMetrics(String bidder, String accountId) {
        final String metricsBidderName = resolveMetricsBidderName(bidder);
        forAdapter(metricsBidderName).request().incCounter(MetricName.nobid);
        if (accountMetricsVerbosity.forAccount(accountId).isAtLeast(AccountMetricsVerbosityLevel.detailed)) {
            forAccount(accountId).forAdapter(metricsBidderName).request().incCounter(MetricName.nobid);
        }
    }

    public void updateAdapterRequestGotbidsMetrics(String bidder, String accountId) {
        final String metricsBidderName = resolveMetricsBidderName(bidder);
        forAdapter(metricsBidderName).request().incCounter(MetricName.gotbids);
        if (accountMetricsVerbosity.forAccount(accountId).isAtLeast(AccountMetricsVerbosityLevel.detailed)) {
            forAccount(accountId).forAdapter(metricsBidderName).request().incCounter(MetricName.gotbids);
        }
    }

    public void updateAdapterBidMetrics(String bidder, String accountId, long cpm, boolean isAdm, String bidType) {
        final String metricsBidderName = resolveMetricsBidderName(bidder);
        final AdapterMetrics adapterMetrics = forAdapter(metricsBidderName);
        adapterMetrics.updateHistogram(MetricName.prices, cpm);
        adapterMetrics.incCounter(MetricName.bids_received);
        adapterMetrics.forBidType(bidType)
                .incCounter(isAdm ? MetricName.adm_bids_received : MetricName.nurl_bids_received);

        if (accountMetricsVerbosity.forAccount(accountId).isAtLeast(AccountMetricsVerbosityLevel.detailed)) {
            final AdapterMetrics accountAdapterMetrics = forAccount(accountId).forAdapter(metricsBidderName);
            accountAdapterMetrics.updateHistogram(MetricName.prices, cpm);
            accountAdapterMetrics.incCounter(MetricName.bids_received);
        }
    }

    public void updateAdapterRequestErrorMetric(String bidder, MetricName errorMetric) {
        forAdapter(resolveMetricsBidderName(bidder)).request().incCounter(errorMetric);
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

    public void updateUserSyncGdprPreventMetric(String bidder) {
        userSync().forBidder(bidder).incCounter(MetricName.gdpr_prevent);
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

    public void updateCookieSyncGdprPreventMetric(String bidder) {
        cookieSync().forBidder(resolveMetricsBidderName(bidder)).incCounter(MetricName.gdpr_prevent);
    }

    public void updateGdprMaskedMetric(String bidder) {
        forAdapter(bidder).incCounter(MetricName.gdpr_masked);
    }

    public void updateConnectionAcceptErrors() {
        incCounter(MetricName.connection_accept_errors);
    }

    public void updateDatabaseQueryTimeMetric(long millis) {
        updateTimer(MetricName.db_query_time, millis);
    }

    public void updateDatabaseCircuitBreakerMetric(boolean opened) {
        if (opened) {
            incCounter(MetricName.db_circuitbreaker_opened);
        } else {
            incCounter(MetricName.db_circuitbreaker_closed);
        }
    }

    public void updateHttpClientCircuitBreakerMetric(boolean opened) {
        if (opened) {
            incCounter(MetricName.httpclient_circuitbreaker_opened);
        } else {
            incCounter(MetricName.httpclient_circuitbreaker_closed);
        }
    }

    public void updateGeoLocationMetric(boolean successful) {
        incCounter(MetricName.geolocation_requests);
        if (successful) {
            incCounter(MetricName.geolocation_successful);
        } else {
            incCounter(MetricName.geolocation_fail);
        }
    }

    public void updateGeoLocationCircuitBreakerMetric(boolean opened) {
        if (opened) {
            incCounter(MetricName.geolocation_circuitbreaker_opened);
        } else {
            incCounter(MetricName.geolocation_circuitbreaker_closed);
        }
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

    public void updateCacheRequestSuccessTime(long timeElapsed) {
        updateTimer(MetricName.prebid_cache_request_success_time, timeElapsed);
    }

    public void updateCacheRequestFailedTime(long timeElapsed) {
        updateTimer(MetricName.prebid_cache_request_error_time, timeElapsed);
    }
}
