package org.prebid.server.hooks.modules.rule.engine.core.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class WeightedList<T> {

    private static final double EPSILON = 1e-6;

    private final List<WeightedEntry<T>> entries;

    public WeightedList(List<WeightedEntry<T>> entries) {
        validateEntries(entries);

        this.entries = prepareEntries(entries);
    }

    private void validateEntries(List<WeightedEntry<T>> entries) {
        Objects.requireNonNull(entries);

        if (entries.isEmpty()) {
            throw new IllegalArgumentException("Weighted list cannot be empty");
        }

        final double sum = entries.stream().mapToDouble(WeightedEntry::getWeight).sum();
        if (sum > WeightedEntry.MAX_WEIGHT + EPSILON) {
            throw new IllegalArgumentException(
                    "Weighted list weights sum must be less than " + WeightedEntry.MAX_WEIGHT);
        }
    }

    private List<WeightedEntry<T>> prepareEntries(List<WeightedEntry<T>> entries) {
        final List<WeightedEntry<T>> result = new ArrayList<>(entries.size());
        double cumulativeSum = 0;

        for (WeightedEntry<T> entry : entries) {
            cumulativeSum += entry.getWeight();
            result.add(WeightedEntry.of(cumulativeSum, entry.getValue()));
        }

        return result;
    }

    public T getForSeed(double seed) {
        if (seed < 0 || seed > WeightedEntry.MAX_WEIGHT) {
            throw new IllegalArgumentException("Seed number must be between 0 and " + WeightedEntry.MAX_WEIGHT);
        }

        for (WeightedEntry<T> entry : entries) {
            if (seed <= entry.getWeight()) {
                return entry.getValue();
            }
        }

        throw new NoMatchingValueException("No entry found for seed " + seed);
    }
}
