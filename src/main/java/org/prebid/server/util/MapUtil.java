package org.prebid.server.util;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class MapUtil {

    private MapUtil() {
    }

    public static <T, U> Map<T, U> merge(Map<T, U> left, Map<T, U> right) {
        final Map<T, U> merged = new HashMap<>(left);
        merged.putAll(right);

        return Collections.unmodifiableMap(merged);
    }

    public static <T, U, P extends Collection<U>> Map<T, P> collectionMerge(Map<T, P> left, Map<T, P> right) {
        final Map<T, P> merged = new HashMap<>(left);
        right.forEach((key, value) -> {
            if (merged.containsKey(key)) {
                merged.get(key).addAll(value);
            } else {
                merged.put(key, value);
            }
        });

        return Collections.unmodifiableMap(merged);
    }
}
