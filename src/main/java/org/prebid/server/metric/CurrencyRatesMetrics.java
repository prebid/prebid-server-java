package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;

import java.util.Objects;
import java.util.function.Function;

/**
 * Circuit breaker metrics support.
 */
class CurrencyRatesMetrics extends UpdatableMetrics {

    private static final String SUFFIX = ".count";

    CurrencyRatesMetrics(MetricRegistry metricRegistry, CounterType counterType) {
        super(Objects.requireNonNull(metricRegistry), Objects.requireNonNull(counterType), nameCreator());
    }

    private static Function<MetricName, String> nameCreator() {
        return metricName -> String.format("currency-rates.%s%s", metricName.toString(), SUFFIX);
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
