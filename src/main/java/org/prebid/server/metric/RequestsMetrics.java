package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;

public class RequestsMetrics {

    private static final String PREFIX = "requests";

    private final ActivitiesMetrics activitiesMetrics;

    RequestsMetrics(MetricRegistry metricRegistry, CounterType counterType) {
        activitiesMetrics = new ActivitiesMetrics(metricRegistry, counterType, PREFIX);
    }

    ActivitiesMetrics activities() {
        return activitiesMetrics;
    }
}
