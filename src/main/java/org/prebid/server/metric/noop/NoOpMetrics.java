package org.prebid.server.metric.noop;

import org.prebid.server.metric.AccountMetrics;
import org.prebid.server.metric.AdapterMetrics;
import org.prebid.server.metric.CookieSyncMetrics;
import org.prebid.server.metric.Metrics;

public class NoOpMetrics extends NoOpUpdatableMetrics implements Metrics {

    private final AccountMetrics accountMetrics;
    private final AdapterMetrics adapterMetrics;
    private final CookieSyncMetrics cookieSyncMetrics;

    public NoOpMetrics() {
        this.accountMetrics = new NoOpAccountMetrics();
        this.adapterMetrics = new NoOpAdapterMetrics();
        this.cookieSyncMetrics = new NoOpCookieSyncMetrics();
    }

    @Override
    public AccountMetrics forAccount(String account) {
        return this.accountMetrics;
    }

    @Override
    public AdapterMetrics forAdapter(String adapterType) {
        return this.adapterMetrics;
    }

    @Override
    public CookieSyncMetrics cookieSync() {
        return this.cookieSyncMetrics;
    }

}
