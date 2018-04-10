package org.prebid.server.metric;

public interface Metrics extends UpdatableMetrics {

    AccountMetrics forAccount(String account);

    AdapterMetrics forAdapter(String adapterType);

    CookieSyncMetrics cookieSync();
}
