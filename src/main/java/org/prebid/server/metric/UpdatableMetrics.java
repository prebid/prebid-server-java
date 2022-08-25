package org.prebid.server.metric;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.LongSupplier;

class UpdatableMetrics {

    private final MeterRegistry meterRegistry;
    private final Function<MetricName, String> nameCreator;
    // not thread-safe maps are intentionally used here because it's harmless in this particular case - eventually
    // this all boils down to metrics lookup by underlying metric registry and that operation is guaranteed to be
    // thread-safe
    private final Map<MetricName, String> metricNames;

    UpdatableMetrics(MeterRegistry meterRegistry, Function<MetricName, String> nameCreator) {
        this.meterRegistry = meterRegistry;
        this.nameCreator = nameCreator;
        metricNames = new EnumMap<>(MetricName.class);
    }

    /**
     * Increments metric's counter.
     */
    void incCounter(MetricName metricName) {
        meterRegistry.counter(name(metricName)).increment();
    }

    /**
     * Increments metric's counter.
     */
    void incCounter(MetricName metricName, String... tags) {
        meterRegistry.counter(name(metricName), Tags.of(tags)).increment();
    }

    /**
     * Increments metric's counter.
     */
    void incCounter(MetricName metricName, Iterable<MetricTag> tags, String... extraTags) {
        meterRegistry.counter(name(metricName), tags(tags).and(extraTags)).increment();
    }

    /**
     * Increments metric's counter.
     */
    void incCounter(MetricName metricName, MetricTag... tags) {
        meterRegistry.counter(name(metricName), tags(tags)).increment();
    }

    /**
     * Increments metric's counter on a given value.
     */
    void incCounter(MetricName metricName, long value) {
        meterRegistry.counter(name(metricName)).increment(value);
    }

    /**
     * Increments metric's counter on a given value.
     */
    void incCounter(MetricName metricName, long value, String... tags) {
        meterRegistry.counter(name(metricName), Tags.of(tags)).increment(value);
    }

    /**
     * Increments metric's counter on a given value.
     */
    void incCounter(MetricName metricName, long value, MetricTag... tags) {
        meterRegistry.counter(name(metricName), tags(tags)).increment(value);
    }

    /**
     * Increments metric's counter on a given value.
     */
    void incCounter(MetricName metricName, long value, Iterable<MetricTag> tags, String... extraTags) {
        meterRegistry.counter(name(metricName), tags(tags).and(extraTags)).increment(value);
    }

    /**
     * Updates metric's timer with a given value.
     */
    void updateTimer(MetricName metricName, long millis) {
        meterRegistry.timer(name(metricName)).record(millis, TimeUnit.MILLISECONDS);
    }

    /**
     * Updates metric's timer with a given value.
     */
    void updateTimer(MetricName metricName, long millis, MetricTag... tags) {
        meterRegistry.timer(name(metricName), tags(tags)).record(millis, TimeUnit.MILLISECONDS);
    }

    /**
     * Updates metric's timer with a given value.
     */
    void updateTimer(MetricName metricName, long millis, Iterable<MetricTag> tags, String... extraTags) {
        meterRegistry.timer(name(metricName), tags(tags).and(extraTags)).record(millis, TimeUnit.MILLISECONDS);
    }

    /**
     * Updates metric's timer with a given value.
     */
    void updateTimer(MetricName metricName, long millis, String... tags) {
        meterRegistry.timer(name(metricName), Tags.of(tags)).record(millis, TimeUnit.MILLISECONDS);
    }

    /**
     * Updates metric's histogram with a given value.
     */
    void updateHistogram(MetricName metricName, long value) {
        meterRegistry.summary(name(metricName)).record(value);
    }

    /**
     * Updates metric's histogram with a given value.
     */
    void updateHistogram(MetricName metricName, long value, String... tags) {
        meterRegistry.summary(name(metricName), tags).record(value);
    }

    /**
     * Updates metric's histogram with a given value.
     */
    void updateHistogram(MetricName metricName, long value, MetricTag... tags) {
        meterRegistry.summary(name(metricName), tags(tags)).record(value);
    }

    /**
     * Updates metric's histogram with a given value.
     */
    void updateHistogram(MetricName metricName, long value, Iterable<MetricTag> tags, String... extraTags) {
        meterRegistry.summary(name(metricName), tags(tags).and(extraTags)).record(value);
    }

    void createGauge(MetricName metricName, LongSupplier supplier) {
        meterRegistry.gauge(name(metricName), supplier.getAsLong());
    }

    void createGauge(MetricName metricName, LongSupplier supplier, String... tags) {
        meterRegistry.gauge(name(metricName), Tags.of(tags), supplier.getAsLong());
    }

    void createGauge(MetricName metricName, LongSupplier supplier, MetricTag... tags) {
        meterRegistry.gauge(name(metricName), tags(tags), supplier.getAsLong());
    }

    void createGauge(MetricName metricName, LongSupplier supplier, Iterable<MetricTag> tags, String... extraTags) {
        meterRegistry.gauge(name(metricName), tags(tags).and(extraTags), supplier.getAsLong());
    }

    void removeMetric(MetricName metricName) {
        meterRegistry.remove(meterRegistry.find(name(metricName)).meter());
    }

    void removeMetric(MetricName metricName, String... tags) {
        meterRegistry.remove(meterRegistry.find(name(metricName)).tags(Tags.of(tags)).meter());
    }

    void removeMetric(MetricName metricName, MetricTag... tags) {
        meterRegistry.remove(meterRegistry.find(name(metricName)).tags(tags(tags)).meter());
    }

    void removeMetric(MetricName metricName, Iterable<MetricTag> tags, String... extraTags) {
        meterRegistry.remove(meterRegistry.find(name(metricName)).tags(tags(tags).and(extraTags)).meter());
    }

    private String name(MetricName metricName) {
        return metricNames.computeIfAbsent(metricName, key -> nameCreator.apply(metricName));
    }

    private Tags tags(MetricTag... metricTags) {
        Tags tags = Tags.empty();

        for (MetricTag metricTag : metricTags) {
            tags = tags.and(metricTag.toTag());
        }

        return tags;
    }

    private Tags tags(Iterable<MetricTag> metricTags) {
        Tags tags = Tags.empty();

        for (MetricTag metricTag : metricTags) {
            tags = tags.and(metricTag.toTag());
        }

        return tags;
    }
}
