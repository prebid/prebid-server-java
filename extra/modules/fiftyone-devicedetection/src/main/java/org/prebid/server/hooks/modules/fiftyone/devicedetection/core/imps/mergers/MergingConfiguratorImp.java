package org.prebid.server.hooks.modules.fiftyone.devicedetection.core.imps.mergers;

import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.MergingConfigurator;

import java.util.List;

/**
 * Facilitates declarative description of a set of properties to be copied from one object to another.
 *
 * @param <Builder> Type of writable object to copy properties into.
 * @param <ConfigFragment> Type of readable object to copy values from.
 */
public final class MergingConfiguratorImp<Builder, ConfigFragment> implements MergingConfigurator<Builder, ConfigFragment> {
    private final List<PropertyMerge<Builder, ConfigFragment>> propertiesToMerge;

    public MergingConfiguratorImp(List<PropertyMerge<Builder, ConfigFragment>> propertiesToMerge) {
        this.propertiesToMerge = propertiesToMerge;
    }

    @Override
    public void accept(Builder builder, ConfigFragment configFragment) {
        if (configFragment == null) {
            return;
        }
        for (PropertyMerge<Builder, ConfigFragment> nextMerge: propertiesToMerge) {
            nextMerge.copySetting(builder, configFragment);
        }
    }
}
