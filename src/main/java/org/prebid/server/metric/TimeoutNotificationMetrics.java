package org.prebid.server.metric;

import io.micrometer.core.instrument.MeterRegistry;

import java.util.Objects;

/**
 * Contains user sync metrics for a bidders metrics support.
 */
class TimeoutNotificationMetrics extends UpdatableMetrics {

    TimeoutNotificationMetrics(MeterRegistry meterRegistry, CounterType counterType) {
        super(Objects.requireNonNull(meterRegistry), Objects.requireNonNull(counterType),
                metricName -> "timeout_notification." + metricName);
    }
}
