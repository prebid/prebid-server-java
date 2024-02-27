package org.prebid.server.util;

import org.prebid.server.util.algorithms.ListsUnionView;

import java.util.List;

public class ListUtil {

    public static <T> List<T> union(List<? extends T> first, List<? extends T> second) {
        return new ListsUnionView<>(first, second);
    }
}
