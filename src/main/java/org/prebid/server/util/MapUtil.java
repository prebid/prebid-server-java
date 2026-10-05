package org.prebid.server.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class MapUtil {

    private MapUtil() {
    }

    public static <T, U> Map<T, U> merge(Map<T, U> left, Map<T, U> right) {
        final Map<T, U> merged = new HashMap<>(left);
        merged.putAll(right);

        return Collections.unmodifiableMap(merged);
    }

    public static <K, V1, V2> Map<K, V2> mapValues(Map<K, V1> map, Function<V1, V2> transform) {
        return mapValues(map, (ignored, value) -> transform.apply(value));
    }

    public static <K, V1, V2> Map<K, V2> mapValues(Map<K, V1> map, BiFunction<K, V1, V2> transform) {
        return map.entrySet().stream().collect(
                Collectors.toMap(Map.Entry::getKey, entry -> transform.apply(entry.getKey(), entry.getValue())));
    }

    public static <K, V1, V2> Function<Map.Entry<K, V1>, Map.Entry<K, V2>> mapEntryValueMapper(
            BiFunction<K, V1, V2> transform) {

        return mapEntryMapper((key, value) -> Map.entry(key, transform.apply(key, value)));
    }

    public static <K, V, T> Function<Map.Entry<K, V>, T> mapEntryMapper(BiFunction<K, V, T> transform) {
        return entry -> transform.apply(entry.getKey(), entry.getValue());
    }
}
