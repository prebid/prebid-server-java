package org.prebid.server.metric;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

public class Metric {

    private MeterRegistry meterRegistry;

    private final String metricName;
    private List<Tag> metricTags;

    public Metric(MetricName metricName, MeterRegistry meterRegistry) {
        this.metricName = metricName.toString();
        this.metricTags = new ArrayList<Tag>();
        this.meterRegistry = meterRegistry;
    }

    public Metric withTag(String key, String value) {
        this.addTag(Tag.of(key, value));
        return this;
    }

    protected void addTag(Tag metricTag) {
        this.metricTags.add(metricTag);
    }

    public void incCounter() {
        meterRegistry.counter(metricName, metricTags).increment();
    }

    public void incCounter(long value) {
        meterRegistry.counter(metricName, metricTags).increment(value);
    }

    public void updateTimer(long millis) {
        meterRegistry.timer(metricName, metricTags).record(millis, TimeUnit.MILLISECONDS);
    }

    public void updateHistogram(long value) {
        meterRegistry.summary(metricName, metricTags).record(value);
    }

    public void createGauge(LongSupplier supplier) {
        meterRegistry.gauge(metricName, metricTags, supplier.getAsLong());
    }

    public void removeMetric() {
        meterRegistry.remove(meterRegistry.find(metricName).tags(metricTags).meter());
    }
}
