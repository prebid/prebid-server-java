package org.prebid.server.metric;

import io.micrometer.core.instrument.MeterRegistry;

import java.util.Objects;
import java.util.function.Function;

/**
 * Contains user sync metrics for a bidders metrics support.
 */
class PrivacyMetrics extends UpdatableMetrics {

    private final USPrivacyMetrics usPrivacyMetrics;
    private final TcfMetrics tcfMetrics;

    PrivacyMetrics(MeterRegistry meterRegistry) {
        super(Objects.requireNonNull(meterRegistry),
                metricName -> "privacy." + metricName);
        usPrivacyMetrics = new USPrivacyMetrics(meterRegistry, "privacy");
        tcfMetrics = new TcfMetrics(meterRegistry, "privacy");
    }

    USPrivacyMetrics usp() {
        return usPrivacyMetrics;
    }

    TcfMetrics tcf() {
        return tcfMetrics;
    }

    static class USPrivacyMetrics extends UpdatableMetrics {

        USPrivacyMetrics(MeterRegistry meterRegistry, String prefix) {
            super(Objects.requireNonNull(meterRegistry),
                    nameCreator(Objects.requireNonNull(prefix)));
        }

        private static Function<MetricName, String> nameCreator(String prefix) {
            return metricName -> "%s.usp.%s".formatted(prefix, metricName);
        }
    }
}
