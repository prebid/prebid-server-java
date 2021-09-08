package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;

import java.util.Objects;

/**
 * Contains user sync metrics for a bidders metrics support.
 */
class TimeoutNotificationMetrics extends UpdatableMetrics {

    TimeoutNotificationMetrics(MetricRegistry metricRegistry, CounterType counterType) {
        super(Objects.requireNonNull(metricRegistry), Objects.requireNonNull(counterType),
                metricName -> String.format("timeout_notification.%s", metricName.toString()));
    }
}
