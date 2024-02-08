package org.prebid.server.util;

import org.prebid.server.util.algorithms.IterablesUnionView;

public class IterableUtil {

    private IterableUtil() {
    }

    public static <T> Iterable<T> union(Iterable<? extends T> first, Iterable<? extends T> second) {
        return new IterablesUnionView<>(first, second);
    }
}
