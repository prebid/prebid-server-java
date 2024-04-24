package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core;

import java.util.function.Function;
import java.util.function.Predicate;

public record SimplePropertyMerge<SetterFactory, ValueSource, Value>(
        SetterFactory setter,
        Function<ValueSource, Value> getter,
        Predicate<Value> isUsable
) {
}
