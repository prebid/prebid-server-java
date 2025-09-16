package org.prebid.server.util.algorithms.random;

import java.util.function.Function;

public class RandomPositiveWeightedEntrySupplier<E> extends RandomAnyWeightedEntrySupplier<E> {

    public RandomPositiveWeightedEntrySupplier(Function<E, Integer> weightExtractor) {
        super(weightExtractor);
    }

    protected int weight(E entry) {
        final int weight = weightExtractor.apply(entry);
        if (weight > 0) {
            return weight;
        }

        throw new IllegalArgumentException("Entry weight must be greater than 0.");
    }
}
