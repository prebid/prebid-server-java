package org.prebid.server.metric.noop;

import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.UpdatableMetrics;

public class NoOpUpdatableMetrics implements UpdatableMetrics {

    @Override
    public void incCounter(MetricName metricName) {

    }

    @Override
    public void incCounter(MetricName metricName, long value) {

    }

    @Override
    public void updateTimer(MetricName metricName, long millis) {

    }

    @Override
    public void updateHistogram(MetricName metricName, long value) {

    }
}
