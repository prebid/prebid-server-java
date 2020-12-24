package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;

import java.util.Objects;
import java.util.function.Function;

/**
 * Request metrics support.
 */
class ResponseMetrics extends UpdatableMetrics {

    private final ValidationMetrics validationMetrics;

    ResponseMetrics(MetricRegistry metricRegistry, CounterType counterType, String prefix) {
        super(Objects.requireNonNull(metricRegistry), Objects.requireNonNull(counterType),
                nameCreator(createPrefix(Objects.requireNonNull(prefix))));

        validationMetrics = new ValidationMetrics(metricRegistry, counterType, createPrefix(prefix));
    }

    private static Function<MetricName, String> nameCreator(String prefix) {
        return metricName -> String.format("%s.%s", prefix, metricName.toString());
    }

    private static String createPrefix(String prefix) {
        return String.format("%s.response", prefix);
    }

    ValidationMetrics validation() {
        return validationMetrics;
    }
}
