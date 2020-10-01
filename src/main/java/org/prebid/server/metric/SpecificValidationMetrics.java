package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;

import java.util.Objects;
import java.util.function.Function;

/**
 * Request metrics support.
 */
class SpecificValidationMetrics extends UpdatableMetrics {

    SpecificValidationMetrics(
            MetricRegistry metricRegistry, CounterType counterType, String prefix, String validation) {

        super(Objects.requireNonNull(metricRegistry), Objects.requireNonNull(counterType),
                nameCreator(createPrefix(Objects.requireNonNull(prefix), Objects.requireNonNull(validation))));
    }

    private static Function<MetricName, String> nameCreator(String prefix) {
        return metricName -> String.format("%s.%s", prefix, metricName.toString());
    }

    private static String createPrefix(String prefix, String validation) {
        return String.format("%s.%s", prefix, validation);
    }
}
