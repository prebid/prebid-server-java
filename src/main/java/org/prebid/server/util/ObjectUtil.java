package org.prebid.server.util;

import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class ObjectUtil {

    private ObjectUtil() {
    }

    @SafeVarargs
    public static <T> T firstNonNull(Supplier<T>... suppliers) {
        return Stream.of(suppliers)
                .map(Supplier::get)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    public static <S, T> T getIfNotNull(S source, Function<S, T> getter) {
        return source != null ? getter.apply(source) : null;
    }

    public static <S, T> T getIfNotNullOrDefault(S source, Function<S, T> getter, Supplier<T> defaultSupplier) {
        return source != null ? getter.apply(source) : defaultSupplier.get();
    }
}
