package org.prebid.server.hooks.modules.fiftyone.devicedetection.core.imps.pipelinebuilders;

import fiftyone.devicedetection.DeviceDetectionOnPremisePipelineBuilder;
import fiftyone.pipeline.engines.Constants;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.imps.mergers.MergingConfiguratorImp;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.imps.mergers.PropertyMergeImp;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.PerformanceConfig;

import java.util.List;

public final class PipelinePerformanceConfigurator implements PipelineConfigurator<PerformanceConfig> {
    private static final MergingConfiguratorImp<DeviceDetectionOnPremisePipelineBuilder, PerformanceConfig> MERGER = new MergingConfiguratorImp<>(List.of(
            new PropertyMergeImp<>(PerformanceConfig::getProfile, s -> !s.isEmpty(), (pipelineBuilder, profile) -> {
                final String lowercasedProfile = profile.toLowerCase();
                for (Constants.PerformanceProfiles nextProfile: Constants.PerformanceProfiles.values()) {
                    if (nextProfile.name().toLowerCase().equals(lowercasedProfile)) {
                        pipelineBuilder.setPerformanceProfile(nextProfile);
                        return;
                    }
                }
            }),
            new PropertyMergeImp<>(PerformanceConfig::getConcurrency, v -> true, DeviceDetectionOnPremisePipelineBuilder::setConcurrency),
            new PropertyMergeImp<>(PerformanceConfig::getDifference, v -> true, DeviceDetectionOnPremisePipelineBuilder::setDifference),
            new PropertyMergeImp<>(PerformanceConfig::getAllowUnmatched, v -> true, DeviceDetectionOnPremisePipelineBuilder::setAllowUnmatched),
            new PropertyMergeImp<>(PerformanceConfig::getDrift, v -> true, DeviceDetectionOnPremisePipelineBuilder::setDrift)));

    @Override
    public void accept(DeviceDetectionOnPremisePipelineBuilder pipelineBuilder, PerformanceConfig performanceConfig) {
        MERGER.applyProperties(pipelineBuilder, performanceConfig);
    }
}
