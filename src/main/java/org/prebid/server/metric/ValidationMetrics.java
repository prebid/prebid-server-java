package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;

import java.util.Objects;
import java.util.function.Function;

/**
 * Request metrics support.
 */
class ValidationMetrics extends UpdatableMetrics {

    private final SpecificValidationMetrics sizeValidationMetrics;
    private final SpecificValidationMetrics secureValidationMetrics;

    ValidationMetrics(MetricRegistry metricRegistry, CounterType counterType, String prefix) {
        super(Objects.requireNonNull(metricRegistry), Objects.requireNonNull(counterType),
                nameCreator(createPrefix(Objects.requireNonNull(prefix))));

        sizeValidationMetrics = new SpecificValidationMetrics(
                metricRegistry, counterType, createPrefix(prefix), "size");
        secureValidationMetrics = new SpecificValidationMetrics(
                metricRegistry, counterType, createPrefix(prefix), "secure");
    }

    private static Function<MetricName, String> nameCreator(String prefix) {
        return metricName -> "%s.%s".formatted(prefix, metricName);
    }

    private static String createPrefix(String prefix) {
        return prefix + ".validation";
    }

    SpecificValidationMetrics size() {
        return sizeValidationMetrics;
    }

    SpecificValidationMetrics secure() {
        return secureValidationMetrics;
    }
}
