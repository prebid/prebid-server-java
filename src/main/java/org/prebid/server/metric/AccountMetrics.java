package org.prebid.server.metric;

public interface AccountMetrics extends UpdatableMetrics {

    AdapterMetrics forAdapter(String adapterType);

}
