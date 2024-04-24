package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core;

import java.util.function.BiConsumer;

public interface MergingConfigurator<Builder, ConfigFragment> extends BiConsumer<Builder, ConfigFragment> {
    default void applyProperties(Builder builder, ConfigFragment configFragment) {
        accept(builder, configFragment);
    }
}
