package org.prebid.server.metric;

import io.micrometer.core.instrument.MeterRegistry;

import java.util.Objects;
import java.util.function.Function;

/**
 * Request metrics support.
 */
class ResponseMetrics extends UpdatableMetrics {

    private final ValidationMetrics validationMetrics;

    ResponseMetrics(MeterRegistry meterRegistry, String prefix) {
        super(Objects.requireNonNull(meterRegistry),
                nameCreator(createPrefix(Objects.requireNonNull(prefix))));

        validationMetrics = new ValidationMetrics(meterRegistry, createPrefix(prefix));
    }

    private static Function<MetricName, String> nameCreator(String prefix) {
        return metricName -> "%s.%s".formatted(prefix, metricName);
    }

    private static String createPrefix(String prefix) {
        return prefix + ".response";
    }

    ValidationMetrics validation() {
        return validationMetrics;
    }
}
