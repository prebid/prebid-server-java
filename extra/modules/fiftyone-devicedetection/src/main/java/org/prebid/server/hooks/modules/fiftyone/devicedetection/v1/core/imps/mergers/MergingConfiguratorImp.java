package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps.mergers;

import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.MergingConfigurator;

import java.util.List;

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
