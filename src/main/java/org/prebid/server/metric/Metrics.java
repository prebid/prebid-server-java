package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;
import org.prebid.server.bidder.BidderCatalog;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Packages different categories (instances) of metrics
 */
public class Metrics extends UpdatableMetrics {

    private final Function<String, AccountMetrics> accountMetricsCreator;
    private final Function<String, AdapterMetrics> adapterMetricsCreator;
    // not thread-safe maps are intentionally used here because it's harmless in this particular case - eventually
    // this all boils down to metrics lookup by underlying metric registry and that operation is guaranteed to be
    // thread-safe
    private final Map<String, AccountMetrics> accountMetrics;
    private final Map<String, AdapterMetrics> adapterMetrics;
    private final CookieSyncMetrics cookieSyncMetrics;

    public Metrics(MetricRegistry metricRegistry, CounterType counterType, BidderCatalog bidderCatalog) {
        super(metricRegistry, counterType, Enum::name);
        Objects.requireNonNull(bidderCatalog);

        adapterMetricsCreator =
                adapterType -> createAdapterMetrics(metricRegistry, counterType, bidderCatalog, adapterType);
        accountMetricsCreator =
                account -> new AccountMetrics(metricRegistry, counterType, bidderCatalog, account);

        accountMetrics = new HashMap<>();
        adapterMetrics = new HashMap<>();
        cookieSyncMetrics = new CookieSyncMetrics(metricRegistry, counterType);
    }

    private static AdapterMetrics createAdapterMetrics(MetricRegistry metricRegistry, CounterType counterType,
                                                       BidderCatalog bidderCatalog, String adapterType) {
        return bidderCatalog.isActive(adapterType)
                ? new AdapterMetrics(metricRegistry, counterType, adapterType)
                : new DisabledAdapterMetrics(metricRegistry, counterType, adapterType);
    }

    /**
     * Returns existing or creates a new {@link AccountMetrics}.
     */
    public AccountMetrics forAccount(String account) {
        return accountMetrics.computeIfAbsent(account, accountMetricsCreator);
    }

    /**
     * Returns existing or creates a new {@link AdapterMetrics}.
     */
    public AdapterMetrics forAdapter(String adapterType) {
        return adapterMetrics.computeIfAbsent(adapterType, adapterMetricsCreator);
    }

    /**
     * Returns {@link CookieSyncMetrics}.
     */
    public CookieSyncMetrics cookieSync() {
        return cookieSyncMetrics;
    }
}
