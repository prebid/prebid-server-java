package org.prebid.server.protobuf;

import org.apache.commons.collections4.CollectionUtils;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class MapperUtils {

    private MapperUtils() {
    }

    public static <T, U> U mapNotNull(T value, Function<T, U> mapper) {
        return value != null
                ? mapper.apply(value)
                : null;
    }

    public static <T> void setNotNull(T value, Consumer<T> setter) {
        if (value != null) {
            setter.accept(value);
        }
    }

    public static <T, U> List<U> mapList(List<T> values, Function<T, U> mapper) {
        return CollectionUtils.isEmpty(values)
                ? Collections.emptyList()
                : values.stream().map(mapper).toList();
    }
}
