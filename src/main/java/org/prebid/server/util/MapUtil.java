package org.prebid.server.util;

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
}
