package org.prebid.server.metric;

import io.micrometer.core.instrument.Tag;

public class MetricTag {

    private final String key;
    private final String value;

    MetricTag(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public Tag toTag() {
        return Tag.of(this.key, this.value);
    }

    @Override
    public String toString() {
        return this.key + " - " + this.value;
    }
}
