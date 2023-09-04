package org.prebid.server.metric;

import io.micrometer.core.instrument.MeterRegistry;

public class PgMetrics extends UpdatableMetrics {

    PgMetrics(MeterRegistry meterRegistry, CounterType counterType) {
        super(meterRegistry, counterType, metricName -> "pg." + metricName);
    }
}
