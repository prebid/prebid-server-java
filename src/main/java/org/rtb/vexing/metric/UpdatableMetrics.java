package org.rtb.vexing.metric;

import com.codahale.metrics.MetricRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;

class UpdatableMetrics {

    private final MetricRegistry metricRegistry;
    private Function<MetricName, String> nameCreator;
    private final BiConsumer<MetricRegistry, String> incrementer;
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
                incrementer = (registry, metricName) ->
                        registry.counter(metricName, ResettingCounter::new).inc();
                break;
            case counter:
                incrementer = (registry, metricName) -> registry.counter(metricName).inc();
                break;
            case meter:
                incrementer = (registry, metricName) -> registry.meter(metricName).mark();
                break;
            default:
                // to satisfy compiler
                throw new IllegalStateException("Should never happen");
        }
    }

    public void incCounter(MetricName metricName) {
        Objects.requireNonNull(metricName);
        incrementer.accept(metricRegistry, name(metricName));
    }

    public void updateTimerNanos(MetricName metricName, long duration) {
        Objects.requireNonNull(metricName);
        metricRegistry.timer(name(metricName)).update(duration, TimeUnit.NANOSECONDS);
    }

    public void updateHistogram(MetricName metricName, long value) {
        Objects.requireNonNull(metricName);
        // by default histograms with exponentially decaying reservoir (size=1028, alpha=0.015) are created
        metricRegistry.histogram(name(metricName)).update(value);
    }

    private String name(MetricName metricName) {
        return metricNames.computeIfAbsent(metricName, key -> nameCreator.apply(metricName));
    }
}
