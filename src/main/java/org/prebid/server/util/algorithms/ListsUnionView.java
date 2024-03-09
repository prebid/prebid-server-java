package org.prebid.server.util.algorithms;

import java.util.AbstractList;
import java.util.List;
import java.util.Objects;

public class ListsUnionView<T> extends AbstractList<T> implements List<T> {

    private final List<? extends T> first;
    private final List<? extends T> second;

    public ListsUnionView(List<? extends T> first, List<? extends T> second) {
        this.first = Objects.requireNonNull(first);
        this.second = Objects.requireNonNull(second);
    }

    @Override
    public T get(int index) {
        return index < first.size()
                ? first.get(index)
                : second.get(index - first.size());
    }

    @Override
    public int size() {
        return first.size() + second.size();
    }
}
