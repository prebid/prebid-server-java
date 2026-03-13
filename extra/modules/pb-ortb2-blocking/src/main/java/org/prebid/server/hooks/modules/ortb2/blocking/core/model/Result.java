package org.prebid.server.hooks.modules.ortb2.blocking.core.model;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class Result<T> {

    private static final Result<?> EMPTY = of(null, null);

    T value;

    List<String> messages;

    @SuppressWarnings("unchecked")
    public static <T> Result<T> empty() {
        return (Result<T>) EMPTY;
    }

    public static <T> Result<T> withValue(T value) {
        return Result.of(value, null);
    }

    public boolean hasValue() {
        return value != null;
    }
}
