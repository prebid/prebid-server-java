package org.prebid.server.hooks.modules.fiftyone.devicedetection.core.imps.mergers;

import java.util.function.BiConsumer;

/**
 * Simple generic setter signature.
 *
 * @param <Target> Type of writable object.
 * @param <Value> Type of property value.
 */
@FunctionalInterface
public interface ValueSetter<Target, Value> extends BiConsumer<Target, Value> {
    /**
     * Alias for {@link #accept}
     *
     * @param target Object to set property in.
     * @param value Value to set property to.
     */
    default void set(Target target, Value value) {
        accept(target, value);
    }
}
