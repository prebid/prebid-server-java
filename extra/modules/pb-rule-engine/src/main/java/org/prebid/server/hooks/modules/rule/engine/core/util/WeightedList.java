package org.prebid.server.hooks.modules.rule.engine.core.util;

import lombok.EqualsAndHashCode;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode
public class WeightedList<T> {

    private final List<WeightedEntry<T>> entries;
    private final int weightSum;

    public WeightedList(List<WeightedEntry<T>> entries) {
        validateEntries(entries);

        this.weightSum = entries.stream().mapToInt(WeightedEntry::getWeight).sum();
        this.entries = prepareEntries(entries);
    }

    private void validateEntries(List<WeightedEntry<T>> entries) {
        if (CollectionUtils.isEmpty(entries)) {
            throw new IllegalArgumentException("Weighted list cannot be empty");
        }
    }

    private List<WeightedEntry<T>> prepareEntries(List<WeightedEntry<T>> entries) {
        final List<WeightedEntry<T>> result = new ArrayList<>(entries.size());

        int cumulativeSum = 0;

        for (WeightedEntry<T> entry : entries) {
            cumulativeSum += entry.getWeight();
            result.add(WeightedEntry.of(cumulativeSum, entry.getValue()));
        }

        return result;
    }

    public T getForSeed(int seed) {
        if (seed < 0 || seed >= maxSeed()) {
            throw new IllegalArgumentException("Seed number must be between 0 and " + weightSum);
        }

        for (WeightedEntry<T> entry : entries) {
            if (seed < entry.getWeight()) {
                return entry.getValue();
            }
        }

        throw new NoMatchingValueException("No entry found for seed " + seed);
    }

    public int maxSeed() {
        return weightSum;
    }
}
