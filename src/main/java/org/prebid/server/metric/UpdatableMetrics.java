package org.prebid.server.metric;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.LongSupplier;

class UpdatableMetrics {

    private final MeterRegistry meterRegistry;
    private final Function<MetricName, String> nameCreator;
    private final MetricIncrementer incrementer;
    private final CounterType counterType;
    // not thread-safe maps are intentionally used here because it's harmless in this particular case - eventually
    // this all boils down to metrics lookup by underlying metric registry and that operation is guaranteed to be
    // thread-safe
    private final Map<MetricName, String> metricNames;

    UpdatableMetrics(MeterRegistry meterRegistry, CounterType counterType, Function<MetricName, String> nameCreator) {
        this.meterRegistry = meterRegistry;
        this.counterType = counterType;
        this.nameCreator = nameCreator;
        metricNames = new EnumMap<>(MetricName.class);

        incrementer = null;
        // = switch (counterType) {
//            case flushingCounter -> (registry, metricName, value) ->
//                    registry.counter(metricName, ResettingCounter::new).inc(value);
//            case counter -> (registry, metricName, value) -> registry.counter(metricName).inc(value);
//            case meter -> (registry, metricName, value) -> registry.meter(metricName).mark(value);
        // };
    }

    /**
     * Increments metric's counter.
     */
    void incCounter(MetricName metricName) {
        incCounter(metricName, 1);
    }

    /**
     * Increments metric's counter on a given value.
     */
    void incCounter(MetricName metricName, long value) {
        meterRegistry.counter(name(metricName)).increment(value);
    }

    /**
     * Updates metric's timer with a given value.
     */
    void updateTimer(MetricName metricName, long millis) {
        meterRegistry.timer(name(metricName)).record(millis, TimeUnit.MILLISECONDS);
//        metricRegistry.timer(name(metricName)).update(millis, TimeUnit.MILLISECONDS);
    }

    /**
     * Updates metric's histogram with a given value.
     */
    void updateHistogram(MetricName metricName, long value) {
        // by default histograms with exponentially decaying reservoir (size=1028, alpha=0.015) are created
        meterRegistry.summary(name(metricName)).record(value);
        // metricRegistry.histogram(name(metricName)).update(value);
    }

    void createGauge(MetricName metricName, LongSupplier supplier) {
        meterRegistry.gauge(name(metricName), supplier.getAsLong());
//        metricRegistry.gauge(name(metricName), () -> supplier::getAsLong);
    }

    void removeMetric(MetricName metricName) {
        final Meter meter = meterRegistry.find(name(metricName)).meter();
        if (meter != null) {
            meterRegistry.remove(meter);
        }
        //metricRegistry.remove(name(metricName));
    }

    private String name(MetricName metricName) {
        return metricNames.computeIfAbsent(metricName, key -> nameCreator.apply(metricName));
    }

    public CounterType getCounterType() {
        return counterType;
    }

    @FunctionalInterface
    private interface MetricIncrementer {
        void accept(MeterRegistry metricRegistry, String metricName, long value);
    }
}
