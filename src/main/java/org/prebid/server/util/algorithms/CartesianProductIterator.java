package org.prebid.server.util.algorithms;

import org.apache.commons.collections4.IterableUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

public class CartesianProductIterator<T> implements Iterator<List<T>> {

    private final List<Iterable<T>> sets;
    private final List<Iterator<T>> iterators;
    private final ArrayList<T> previousCell;

    public CartesianProductIterator(List<Iterable<T>> sets) {
        this.sets = Objects.requireNonNull(sets);

        iterators = iterators(sets);
        previousCell = new ArrayList<>(sets.size());
    }

    private static <T> List<Iterator<T>> iterators(List<Iterable<T>> sets) {
        final List<Iterator<T>> iterators = new ArrayList<>(sets.size());
        for (Iterable<T> set : sets) {
            if (IterableUtils.isEmpty(set)) {
                return Collections.emptyList();
            }
            iterators.add(set.iterator());
        }
        return iterators;
    }

    @Override
    public boolean hasNext() {
        for (Iterator<?> iterator : iterators) {
            if (iterator.hasNext()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<T> next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }

        if (previousCell.isEmpty()) {
            for (Iterator<T> iterator : iterators) {
                previousCell.add(iterator.next());
            }
            return new ArrayList<>(previousCell);
        }

        return generateCell();
    }

    private List<T> generateCell() {
        for (int i = iterators.size() - 1; i >= 0; i--) {
            if (iterator(i).hasNext()) {
                previousCell.set(i, iterator(i).next());
                break;
            } else {
                resetIterator(i);
                previousCell.set(i, iterator(i).next());
            }
        }

        return new ArrayList<>(previousCell);
    }

    private Iterator<T> iterator(int i) {
        return iterators.get(i);
    }

    private void resetIterator(int i) {
        iterators.set(i, sets.get(i).iterator());
    }
}
