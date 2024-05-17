package org.prebid.server.hooks.modules.fiftyone.devicedetection.core;

import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A reusable container for multiple functions related to the same-ish property in different classes.
 *
 * @param setter Function that allows to set the property in a writable object.
 * @param getter Function that allows getting the property from a readable object.
 * @param isUsable Predicate whether the property value is a reasonable override.
 *
 * @param <SetterFactory> Type of {@link #setter()}.
 * @param <ValueSource> Type of readable object for {@link #getter()}.
 * @param <Value> Type of property value in {@link ValueSource} instance.
 */
public record SimplePropertyMerge<SetterFactory, ValueSource, Value>(
        SetterFactory setter,
        Function<ValueSource, Value> getter,
        Predicate<Value> isUsable
) {
}
