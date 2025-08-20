package org.prebid.server.hooks.modules.rule.engine.core.util;

import lombok.Value;

@Value
public class WeightedEntry<T> {

    int weight;

    T value;

    private WeightedEntry(int weight, T value) {
        this.weight = weight;
        this.value = value;

        if (weight < 0) {
            throw new IllegalArgumentException("Weight must be greater than zero");
        }
    }

    public static <T> WeightedEntry<T> of(int weight, T value) {
        return new WeightedEntry<>(weight, value);
    }
}
