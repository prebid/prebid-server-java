package org.prebid.server.model;

import lombok.Value;

@Value
public class UpdateResult<T> {

    boolean updated;

    T value;

    public static <T> UpdateResult<T> unaltered(T value) {
        return new UpdateResult<>(false, value);
    }

    public static <T> UpdateResult<T> updated(T value) {
        return new UpdateResult<>(true, value);
    }
}
