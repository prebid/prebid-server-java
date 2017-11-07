package org.rtb.vexing.metric;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import org.rtb.vexing.config.ApplicationConfig;

import java.util.Objects;
import java.util.function.BiConsumer;

public class Metrics {

    private final MetricRegistry metricRegistry;
    private final BiConsumer<MetricRegistry, MetricName> incrementer;

    private Metrics(MetricRegistry metricRegistry, BiConsumer<MetricRegistry, MetricName> incrementer) {
        this.metricRegistry = metricRegistry;
        this.incrementer = incrementer;
    }

    public static Metrics create(MetricRegistry metricRegistry, ApplicationConfig config) {
        Objects.requireNonNull(metricRegistry);
        Objects.requireNonNull(config);

        final BiConsumer<MetricRegistry, MetricName> incremeter;

        switch (MetricType.valueOf(config.getString("metrics.metricType"))) {
            case flushingCounter:
                incremeter = (registry, metricName) -> registry.counter(metricName.name(), ResettingCounter::new).inc();
                break;
            case counter:
                incremeter = (registry, metricName) -> registry.counter(metricName.name()).inc();
                break;
            case meter:
                incremeter = (registry, metricName) -> registry.meter(metricName.name()).mark();
                break;
            default:
                // to satisfy compiler
                throw new IllegalStateException("Should never happen");
        }

        return new Metrics(metricRegistry, incremeter);
    }

    public void incCount(MetricName name) {
        incrementer.accept(metricRegistry, Objects.requireNonNull(name));
    }

    private enum MetricType {
        counter, flushingCounter, meter
    }

    private static class ResettingCounter extends Counter {
        @Override
        public long getCount() {
            final long count = super.getCount();
            dec(count);
            return count;
        }
    }
}
