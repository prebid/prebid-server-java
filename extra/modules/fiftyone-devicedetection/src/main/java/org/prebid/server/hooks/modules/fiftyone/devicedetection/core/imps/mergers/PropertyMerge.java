package org.prebid.server.hooks.modules.fiftyone.devicedetection.core.imps.mergers;

import java.util.function.BiConsumer;

/**
 * A function that attempts to populate a single property of {@link Target} instance
 * with the respective value from {@link Source} instance.
 *
 * @param <Target> Type of writable object that is populated
 * @param <Source> Type of readable object with override values
 */
@FunctionalInterface
public interface PropertyMerge<Target, Source> extends BiConsumer<Target, Source> {
    /**
     * Alias for {@link #accept}.
     * Attempts to populate a single property of {@code target} with the respective value from {@code source}.
     *
     * @param target Writable object that is populated
     * @param source Readable object with overrides for {@code target}
     */
    default void copySetting(Target target, Source source) {
        accept(target, source);
    }
}
