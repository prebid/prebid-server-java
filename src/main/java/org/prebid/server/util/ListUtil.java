package org.prebid.server.util;

import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.util.algorithms.ListsUnionView;

import java.util.List;

public class ListUtil {

    private ListUtil() {
    }

    public static <T> List<T> union(List<? extends T> first, List<? extends T> second) {
        return new ListsUnionView<>(first, second);
    }

    public static <T> List<T> nullIfEmpty(List<T> value) {
        return CollectionUtils.isEmpty(value) ? null : value;
    }
}
