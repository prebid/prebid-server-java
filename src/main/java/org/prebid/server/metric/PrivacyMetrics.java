package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;

import java.util.Objects;
import java.util.function.Function;

/**
 * Contains user sync metrics for a bidders metrics support.
 */
class PrivacyMetrics extends UpdatableMetrics {

    private final USPrivacyMetrics usPrivacyMetrics;
    private final TcfMetrics tcfMetrics;

    PrivacyMetrics(MetricRegistry metricRegistry, CounterType counterType) {
        super(Objects.requireNonNull(metricRegistry), Objects.requireNonNull(counterType),
                metricName -> String.format("privacy.%s", metricName.toString()));
        usPrivacyMetrics = new USPrivacyMetrics(metricRegistry, counterType, "privacy");
        tcfMetrics = new TcfMetrics(metricRegistry, counterType, "privacy");
    }

    USPrivacyMetrics usp() {
        return usPrivacyMetrics;
    }

    TcfMetrics tcf() {
        return tcfMetrics;
    }

    static class USPrivacyMetrics extends UpdatableMetrics {

        USPrivacyMetrics(MetricRegistry metricRegistry, CounterType counterType, String prefix) {
            super(Objects.requireNonNull(metricRegistry), Objects.requireNonNull(counterType),
                    nameCreator(Objects.requireNonNull(prefix)));
        }

        private static Function<MetricName, String> nameCreator(String prefix) {
            return metricName -> String.format("%s.usp.%s", prefix, metricName.toString());
        }
    }
}
