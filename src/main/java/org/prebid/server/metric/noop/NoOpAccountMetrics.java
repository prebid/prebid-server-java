package org.prebid.server.metric.noop;

import org.prebid.server.metric.AccountMetrics;
import org.prebid.server.metric.AdapterMetrics;

public class NoOpAccountMetrics extends NoOpUpdatableMetrics implements AccountMetrics {

    private final AdapterMetrics adapterMetrics;

    public NoOpAccountMetrics() {
        this.adapterMetrics = new NoOpAdapterMetrics();
    }

    @Override
    public AdapterMetrics forAdapter(String adapterType) {
        return this.adapterMetrics;
    }

}
