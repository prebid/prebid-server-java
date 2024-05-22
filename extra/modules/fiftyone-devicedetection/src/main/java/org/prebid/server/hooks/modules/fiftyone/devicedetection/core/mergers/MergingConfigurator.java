package org.prebid.server.hooks.modules.fiftyone.devicedetection.core.mergers;

import java.util.List;
import java.util.function.BiPredicate;

/**
 * Facilitates declarative description of a set of properties to be copied from one object to another.
 *
 * @param <Builder> Type of writable object to copy properties into.
 * @param <ConfigFragment> Type of readable object to copy values from.
 */
public final class MergingConfigurator<Builder, ConfigFragment> implements BiPredicate<Builder, ConfigFragment> {
    private final List<PropertyMerge<Builder, ConfigFragment, ?>> propertiesToMerge;

    public MergingConfigurator(List<PropertyMerge<Builder, ConfigFragment, ?>> propertiesToMerge) {
        this.propertiesToMerge = propertiesToMerge;
    }

    @Override
    public boolean test(Builder builder, ConfigFragment configFragment) {
        if (configFragment == null) {
            return false;
        }
        boolean result = false;
        for (PropertyMerge<Builder, ConfigFragment, ?> nextMerge: propertiesToMerge) {
            if (nextMerge.copySingleValue(builder, configFragment)) {
                result = true;
            }
        }
        return result;
    }
}
