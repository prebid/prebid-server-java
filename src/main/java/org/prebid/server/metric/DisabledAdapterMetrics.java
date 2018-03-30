package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;

/**
 * Metrics for disabled bidder. Just ignores all calls.
 */
public class DisabledAdapterMetrics extends AdapterMetrics {

    DisabledAdapterMetrics(MetricRegistry metricRegistry, CounterType counterType,
                           String adapterType) {
        super(metricRegistry, counterType, adapterType);
    }

    @Override
    public void incCounter(MetricName metricName) {
        // no op
    }

    @Override
    public void incCounter(MetricName metricName, long value) {
        // no op
    }

    @Override
    public void updateTimer(MetricName metricName, long millis) {
        // no op
    }

    @Override
    public void updateHistogram(MetricName metricName, long value) {
        // no op
    }
}
