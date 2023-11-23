package org.prebid.server.bidder.appnexus;

import java.util.Objects;

public class SameValueValidator<T> {

    private T sameValue;

    private SameValueValidator() {
    }

    public static <T> SameValueValidator<T> create() {
        return new SameValueValidator<>();
    }

    public boolean isInitialised() {
        return sameValue != null;
    }

    public boolean isInvalid(T value) {
        if (!isInitialised()) {
            sameValue = value;
            return false;
        }

        return !Objects.equals(sameValue, value);
    }

    public T getValue() {
        return sameValue;
    }
}
