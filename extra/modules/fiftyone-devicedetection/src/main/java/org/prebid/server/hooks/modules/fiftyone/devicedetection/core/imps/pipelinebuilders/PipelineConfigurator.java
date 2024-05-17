package org.prebid.server.hooks.modules.fiftyone.devicedetection.core.imps.pipelinebuilders;

import fiftyone.devicedetection.DeviceDetectionOnPremisePipelineBuilder;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.MergingConfigurator;

@FunctionalInterface
public interface PipelineConfigurator<ConfigFragment>
        extends MergingConfigurator<DeviceDetectionOnPremisePipelineBuilder, ConfigFragment> {
}
