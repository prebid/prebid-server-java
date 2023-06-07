package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;
import io.micrometer.core.instrument.MeterRegistry;

public class RequestsMetrics {

    private static final String PREFIX = "requests";

    private final ActivitiesMetrics activitiesMetrics;

    RequestsMetrics(MeterRegistry meterRegistry, CounterType counterType) {
        activitiesMetrics = new ActivitiesMetrics(meterRegistry, counterType, PREFIX);
    }

    ActivitiesMetrics activities() {
        return activitiesMetrics;
    }
}
