package org.prebid.server.metric;

public interface UpdatableMetrics {

    void incCounter(MetricName metricName);

    void incCounter(MetricName metricName, long value);

    void updateTimer(MetricName metricName, long millis);

    void updateHistogram(MetricName metricName, long value);
}
