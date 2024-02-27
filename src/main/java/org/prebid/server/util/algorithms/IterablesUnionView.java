package org.prebid.server.util.algorithms;

import lombok.NonNull;

import java.util.Iterator;
import java.util.Objects;

public class IterablesUnionView<T> implements Iterable<T> {

    private final Iterable<? extends T> first;
    private final Iterable<? extends T> second;

    public IterablesUnionView(Iterable<? extends T> first, Iterable<? extends T> second) {
        this.first = Objects.requireNonNull(first);
        this.second = Objects.requireNonNull(second);
    }

    @NonNull
    @Override
    public Iterator<T> iterator() {
        return new Iterator<>() {

            private final Iterator<? extends T> firstIterator = first.iterator();
            private final Iterator<? extends T> secondIterator = second.iterator();

            @Override
            public boolean hasNext() {
                return firstIterator.hasNext() || secondIterator.hasNext();
            }

            @Override
            public T next() {
                return firstIterator.hasNext()
                        ? firstIterator.next()
                        : secondIterator.next();
            }
        };
    }
}
