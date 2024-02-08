package org.prebid.server.util.algorithms;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

public class RandomWeightedEntrySupplier<E> {

    private final Function<E, Integer> weightExtractor;

    public RandomWeightedEntrySupplier(Function<E, Integer> weightExtractor) {
        this.weightExtractor = Objects.requireNonNull(weightExtractor);
    }

    public E get(Iterable<E> entries) {
        final int totalWeight = totalWeight(entries);

        int randomInt = ThreadLocalRandom.current().nextInt(totalWeight);
        for (E entry : entries) {
            randomInt -= weightExtractor.apply(entry);

            if (randomInt < 0) {
                return entry;
            }
        }

        throw new AssertionError();
    }

    private int totalWeight(Iterable<E> entries) {
        int sum = 0;
        for (E entry : entries) {
            sum += weightExtractor.apply(entry);
        }
        return sum;
    }
}
