package org.prebid.server.hooks.modules.fiftyone.devicedetection.core.mergers;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * A reusable container for multiple functions related to the same-ish property in different classes.
 *
 * @param setter Function that allows to set the property in a writable object.
 * @param getter Function that allows getting the property from a readable object.
 * @param isUsable Predicate whether the property value is a reasonable override.
 *
 * @param <Target> Type of writable object for {@link #setter()}.
 * @param <ValueSource> Type of readable object for {@link #getter()}.
 * @param <Value> Type of property value in {@link ValueSource} instance.
 */
public record PropertyMerge<Target, ValueSource, Value>(
        Function<ValueSource, Value> getter,
        Predicate<Value> isUsable,
        BiConsumer<Target, Value> setter
) {
    public boolean copySingleValue(Target target, ValueSource propertySource) {
        final Value value = getter().apply(propertySource);
        if (value == null || !isUsable().test(value)) {
            return false;
        }
        setter().accept(target, value);
        return true;
    }

    public boolean shouldReplacePropertyIn(ValueSource valueSource) {
        final Value value = getter().apply(valueSource);
        return (value == null || !isUsable().test(value));
    }
}
