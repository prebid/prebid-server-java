package org.prebid.server.hooks.modules.rule.engine.core.util;

import lombok.Getter;

@Getter
public class WeightedEntry<T> {

    public static final double MAX_WEIGHT = 1.0;

    double weight;

    T value;

    private WeightedEntry(double weight, T value) {
        this.weight = weight;
        this.value = value;

        if (weight < 0 || weight > MAX_WEIGHT) {
            throw new IllegalArgumentException("Weight must be between 0 and 100");
        }
    }

    public static <T> WeightedEntry<T> of(double weight, T value) {
        return new WeightedEntry<>(weight, value);
    }
}
