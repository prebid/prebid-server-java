package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

class UpdatableMetrics {

    private final MetricRegistry metricRegistry;
    private final Function<MetricName, String> nameCreator;
    private final MetricIncrementer incrementer;
    // not thread-safe maps are intentionally used here because it's harmless in this particular case - eventually
    // this all boils down to metrics lookup by underlying metric registry and that operation is guaranteed to be
    // thread-safe
    private final Map<MetricName, String> metricNames;

    UpdatableMetrics(MetricRegistry metricRegistry, CounterType counterType, Function<MetricName, String> nameCreator) {
        this.metricRegistry = metricRegistry;
        this.nameCreator = nameCreator;
        metricNames = new HashMap<>();

        switch (counterType) {
            case flushingCounter:
                incrementer = (registry, metricName, value) ->
                        registry.counter(metricName, ResettingCounter::new).inc(value);
                break;
            case counter:
                incrementer = (registry, metricName, value) -> registry.counter(metricName).inc(value);
                break;
            case meter:
                incrementer = (registry, metricName, value) -> registry.meter(metricName).mark(value);
                break;
            default:
                // to satisfy compiler
                throw new IllegalStateException("Should never happen");
        }
    }

    /**
     * Increments metric's counter.
     */
    protected void incCounter(MetricName metricName) {
        incCounter(metricName, 1);
    }

    /**
     * Increments metric's counter on a given value.
     */
    protected void incCounter(MetricName metricName, long value) {
        incrementer.accept(metricRegistry, name(metricName), value);
    }

    protected void decCounter(MetricName metricName) {
        metricRegistry.counter(name(metricName)).dec();
    }

    /**
     * Updates metric's timer with a given value.
     */
    protected void updateTimer(MetricName metricName, long millis) {
        metricRegistry.timer(name(metricName)).update(millis, TimeUnit.MILLISECONDS);
    }

    /**
     * Updates metric's histogram with a given value.
     */
    protected void updateHistogram(MetricName metricName, long value) {
        // by default histograms with exponentially decaying reservoir (size=1028, alpha=0.015) are created
        metricRegistry.histogram(name(metricName)).update(value);
    }

    private String name(MetricName metricName) {
        return metricNames.computeIfAbsent(metricName, key -> nameCreator.apply(metricName));
    }

    @FunctionalInterface
    private interface MetricIncrementer {
        void accept(MetricRegistry metricRegistry, String metricName, long value);
    }
}
