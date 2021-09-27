package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;

public class PgMetrics extends UpdatableMetrics {

    PgMetrics(MetricRegistry metricRegistry, CounterType counterType) {
        super(metricRegistry, counterType, metricName -> String.format("pg.%s", metricName.toString()));
    }
}
