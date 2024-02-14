package org.prebid.server.util.algorithms.random;

import org.apache.commons.collections4.IterableUtils;

import java.util.IntSummaryStatistics;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

public class RandomAnyWeightedEntrySupplier<E> implements RandomWeightedEntrySupplier<E> {

    protected final Function<E, Integer> weightExtractor;

    public RandomAnyWeightedEntrySupplier(Function<E, Integer> weightExtractor) {
        this.weightExtractor = Objects.requireNonNull(weightExtractor);
    }

    public E get(Iterable<E> entries) {
        if (IterableUtils.isEmpty(entries)) {
            throw new IllegalArgumentException("Empty collection provided.");
        }

        final IntSummaryStatistics summaryStatistics = evaluateStatistic(entries);

        final boolean allZeros = summaryStatistics.getSum() == 0;
        final long totalWeight = allZeros
                ? summaryStatistics.getCount()
                : summaryStatistics.getSum();

        final long randomLong = ThreadLocalRandom.current().nextLong(totalWeight);
        return allZeros
                ? IterableUtils.get(entries, (int) randomLong)
                : getEntry(entries, randomLong);
    }

    private IntSummaryStatistics evaluateStatistic(Iterable<E> entries) {
        final IntSummaryStatistics summaryStatistics = new IntSummaryStatistics();
        for (E entry : entries) {
            summaryStatistics.accept(weight(entry));
        }
        return summaryStatistics;
    }

    protected int weight(E entry) {
        return Math.max(weightExtractor.apply(entry), 0);
    }

    private E getEntry(Iterable<E> entries, long entryNumber) {
        long reducedEntryNumber = entryNumber;
        for (E entry : entries) {
            reducedEntryNumber -= weight(entry);

            if (reducedEntryNumber < 0) {
                return entry;
            }
        }

        throw new AssertionError();
    }
}

