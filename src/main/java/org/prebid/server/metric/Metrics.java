package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Defines interface for submitting different kinds of metrics.
 */
public class Metrics extends UpdatableMetrics {

    private final Function<MetricName, RequestStatusMetrics> requestMetricsCreator;
    private final Function<String, AccountMetrics> accountMetricsCreator;
    private final Function<String, AdapterMetrics> adapterMetricsCreator;
    // not thread-safe maps are intentionally used here because it's harmless in this particular case - eventually
    // this all boils down to metrics lookup by underlying metric registry and that operation is guaranteed to be
    // thread-safe
    private final Map<MetricName, RequestStatusMetrics> requestMetrics;
    private final Map<String, AccountMetrics> accountMetrics;
    private final Map<String, AdapterMetrics> adapterMetrics;
    private final CookieSyncMetrics cookieSyncMetrics;

    public Metrics(MetricRegistry metricRegistry, CounterType counterType) {
        super(metricRegistry, counterType, MetricName::toString);

        requestMetricsCreator = requestType -> new RequestStatusMetrics(metricRegistry, counterType, requestType);
        accountMetricsCreator = account -> new AccountMetrics(metricRegistry, counterType, account);
        adapterMetricsCreator = adapterType -> new AdapterMetrics(metricRegistry, counterType, adapterType);
        requestMetrics = new HashMap<>();
        accountMetrics = new HashMap<>();
        adapterMetrics = new HashMap<>();
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

    public void updateRequestTime(long millis) {
        updateTimer(MetricName.request_time, millis);
    }

    public void updateRequestTypeMetric(MetricName requestType, MetricName requestStatus) {
        forRequestType(requestType).incCounter(requestStatus);
    }

    public void updateAccountRequestMetrics(String accountId, MetricName requestType) {
        final AccountMetrics accountMetrics = forAccount(accountId);

        accountMetrics.incCounter(MetricName.requests);
        accountMetrics.requestType().incCounter(requestType);
    }

    public void updateAdapterRequestTypeAndNoCookieMetrics(String bidder, MetricName requestType, boolean noCookie) {
        final AdapterMetrics adapterMetrics = forAdapter(bidder);

        adapterMetrics.requestType().incCounter(requestType);

        if (noCookie) {
            adapterMetrics.incCounter(MetricName.no_cookie_requests);
        }
    }

    public void updateAdapterResponseTime(String bidder, String accountId, int responseTime) {
        final AdapterMetrics adapterMetrics = forAdapter(bidder);
        final AdapterMetrics accountAdapterMetrics = forAccount(accountId).forAdapter(bidder);

        adapterMetrics.updateTimer(MetricName.request_time, responseTime);
        accountAdapterMetrics.updateTimer(MetricName.request_time, responseTime);
    }

    public void updateAdapterRequestNobidMetrics(String bidder, String accountId) {
        forAdapter(bidder).request().incCounter(MetricName.nobid);
        forAccount(accountId).forAdapter(bidder).request().incCounter(MetricName.nobid);
    }

    public void updateAdapterRequestGotbidsMetrics(String bidder, String accountId) {
        forAdapter(bidder).request().incCounter(MetricName.gotbids);
        forAccount(accountId).forAdapter(bidder).request().incCounter(MetricName.gotbids);
    }

    public void updateAdapterBidMetrics(String bidder, String accountId, long cpm, boolean isAdm, String bidType) {
        final AdapterMetrics adapterMetrics = forAdapter(bidder);
        final AdapterMetrics accountAdapterMetrics = forAccount(accountId).forAdapter(bidder);

        adapterMetrics.updateHistogram(MetricName.prices, cpm);
        accountAdapterMetrics.updateHistogram(MetricName.prices, cpm);

        adapterMetrics.incCounter(MetricName.bids_received);
        accountAdapterMetrics.incCounter(MetricName.bids_received);

        adapterMetrics.forBidType(bidType)
                .incCounter(isAdm ? MetricName.adm_bids_received : MetricName.nurl_bids_received);
    }

    public void updateAdapterRequestErrorMetric(String bidder, MetricName errorMetric) {
        forAdapter(bidder).request().incCounter(errorMetric);
    }

    public void updateCookieSyncRequestMetric() {
        incCounter(MetricName.cookie_sync_requests);
    }

    public void updateCookieSyncOptoutMetric() {
        cookieSync().incCounter(MetricName.opt_outs);
    }

    public void updateCookieSyncBadRequestMetric() {
        cookieSync().incCounter(MetricName.bad_requests);
    }

    public void updateCookieSyncSetsMetric(String bidder) {
        cookieSync().forBidder(bidder).incCounter(MetricName.sets);
    }

    public void updateCookieSyncGdprPreventMetric(String bidder) {
        cookieSync().forBidder(bidder).incCounter(MetricName.gdpr_prevent);
    }

    public void updateGdprMaskedMetric(String bidder) {
        forAdapter(bidder).incCounter(MetricName.gdpr_masked);
    }
}
