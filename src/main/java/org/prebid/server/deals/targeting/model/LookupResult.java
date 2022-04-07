package org.prebid.server.deals.targeting.model;

import lombok.Value;

import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

@Value(staticConstructor = "of")
public class LookupResult<T> {

    private static final LookupResult<Object> EMPTY = LookupResult.of(Collections.emptyList());

    List<T> values;

    @SuppressWarnings("unchecked")
    public static <T> LookupResult<T> empty() {
        return (LookupResult<T>) EMPTY;
    }

    public static <T> LookupResult<T> ofValue(T value) {
        return LookupResult.of(Collections.singletonList(value));
    }

    public boolean anyMatch(Predicate<T> matcher) {
        return values.stream().anyMatch(matcher);
    }
}
