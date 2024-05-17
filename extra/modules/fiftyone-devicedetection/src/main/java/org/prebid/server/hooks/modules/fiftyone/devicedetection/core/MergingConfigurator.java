package org.prebid.server.hooks.modules.fiftyone.devicedetection.core;

import java.util.function.BiConsumer;

/**
 * Attempts to copy a set of properties from one object to another.
 *
 * @param <Builder> Type of writable object to accept values.
 * @param <ConfigFragment> Type of readable object to get values from.
 */
@FunctionalInterface
public interface MergingConfigurator<Builder, ConfigFragment> extends BiConsumer<Builder, ConfigFragment> {
    /**
     * Alias for {@link #accept}.
     *
     * @param builder Writable object to accept values.
     * @param configFragment Readable object to get values from.
     */
    default void applyProperties(Builder builder, ConfigFragment configFragment) {
        accept(builder, configFragment);
    }
}
