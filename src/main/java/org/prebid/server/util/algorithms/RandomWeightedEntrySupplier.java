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
        int totalWeight = 0;
        int size = 0;
        for (E entry : entries) {
            totalWeight += weight(entry);
            size++;
        }

        final boolean allZeros = totalWeight == 0;
        totalWeight = allZeros ? size : totalWeight;

        int randomInt = ThreadLocalRandom.current().nextInt(totalWeight);
        for (E entry : entries) {
            randomInt -= allZeros ? 1 : weight(entry);

            if (randomInt < 0) {
                return entry;
            }
        }

        throw new AssertionError();
    }

    private int weight(E entry) {
        return Math.max(weightExtractor.apply(entry), 0);
    }
}
