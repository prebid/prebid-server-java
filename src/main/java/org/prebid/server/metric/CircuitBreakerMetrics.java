package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Circuit breaker metrics support.
 */
class CircuitBreakerMetrics extends UpdatableMetrics {

    private static final String SUFFIX = ".count";

    private final Function<String, NamedCircuitBreakerMetrics> namedCircuitBreakerMetricsCreator;
    private final Map<String, NamedCircuitBreakerMetrics> namedCircuitBreakerMetrics;

    CircuitBreakerMetrics(MetricRegistry metricRegistry, CounterType counterType, MetricName type) {
        super(
                Objects.requireNonNull(metricRegistry),
                Objects.requireNonNull(counterType),
                nameCreator(createPrefix(Objects.requireNonNull(type))));

        namedCircuitBreakerMetricsCreator =
                name -> new NamedCircuitBreakerMetrics(metricRegistry, counterType, createPrefix(type), name);
        namedCircuitBreakerMetrics = new HashMap<>();
    }

    NamedCircuitBreakerMetrics forName(String name) {
        return namedCircuitBreakerMetrics.computeIfAbsent(name, namedCircuitBreakerMetricsCreator);
    }

    private static String createPrefix(MetricName type) {
        return String.format("circuit-breaker.%s", type.toString());
    }

    private static Function<MetricName, String> nameCreator(String prefix) {
        return metricName -> String.format("%s.%s%s", prefix, metricName.toString(), SUFFIX);
    }

    @Override
    void incCounter(MetricName metricName) {
        throw new UnsupportedOperationException();
    }

    @Override
    void incCounter(MetricName metricName, long value) {
        throw new UnsupportedOperationException();
    }

    @Override
    void updateTimer(MetricName metricName, long millis) {
        throw new UnsupportedOperationException();
    }

    @Override
    void updateHistogram(MetricName metricName, long value) {
        throw new UnsupportedOperationException();
    }

    static class NamedCircuitBreakerMetrics extends UpdatableMetrics {

        NamedCircuitBreakerMetrics(MetricRegistry metricRegistry, CounterType counterType, String prefix, String name) {
            super(
                    Objects.requireNonNull(metricRegistry),
                    Objects.requireNonNull(counterType),
                    nameCreator(Objects.requireNonNull(prefix), Objects.requireNonNull(name)));
        }

        private static Function<MetricName, String> nameCreator(String prefix, String name) {
            return metricName -> String.format("%s.named.%s.%s%s", prefix, name, metricName.toString(), SUFFIX);
        }

        @Override
        void incCounter(MetricName metricName) {
            throw new UnsupportedOperationException();
        }

        @Override
        void incCounter(MetricName metricName, long value) {
            throw new UnsupportedOperationException();
        }

        @Override
        void updateTimer(MetricName metricName, long millis) {
            throw new UnsupportedOperationException();
        }

        @Override
        void updateHistogram(MetricName metricName, long value) {
            throw new UnsupportedOperationException();
        }
    }
}
