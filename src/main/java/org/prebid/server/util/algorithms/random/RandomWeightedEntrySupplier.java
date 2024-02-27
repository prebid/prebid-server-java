package org.prebid.server.util.algorithms.random;

public interface RandomWeightedEntrySupplier<E> {

    E get(Iterable<E> entries);
}
