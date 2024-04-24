package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps.pipelinebuilders;

import fiftyone.devicedetection.DeviceDetectionOnPremisePipelineBuilder;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.MergingConfigurator;

@FunctionalInterface
public interface PipelineConfigurator<ConfigFragment>
        extends MergingConfigurator<DeviceDetectionOnPremisePipelineBuilder, ConfigFragment> {
}
