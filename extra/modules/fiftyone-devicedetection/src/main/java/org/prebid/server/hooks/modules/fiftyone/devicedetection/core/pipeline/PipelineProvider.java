package org.prebid.server.hooks.modules.fiftyone.devicedetection.core.pipeline;

import fiftyone.devicedetection.DeviceDetectionOnPremisePipelineBuilder;
import fiftyone.devicedetection.DeviceDetectionPipelineBuilder;
import fiftyone.pipeline.core.flowelements.Pipeline;
import fiftyone.pipeline.engines.Constants;
import fiftyone.pipeline.engines.services.DataUpdateServiceDefault;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.mergers.MergingConfigurator;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.mergers.PropertyMerge;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.DataFile;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.DataFileUpdate;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.PerformanceConfig;

import java.util.List;
import java.util.function.Supplier;

public class PipelineProvider implements Supplier<Pipeline> {
    private final Pipeline pipeline;

    public PipelineProvider(DataFile dataFile, PerformanceConfig performanceConfig) throws Exception {
        final DeviceDetectionOnPremisePipelineBuilder builder = makeBuilder(dataFile, performanceConfig);
        pipeline = builder.build();
    }

    protected DeviceDetectionOnPremisePipelineBuilder makeBuilder(
            DataFile dataFile,
            PerformanceConfig performanceConfig
    ) throws Exception {
        final DeviceDetectionOnPremisePipelineBuilder builder = makeRawBuilder(dataFile);
        applyUpdateOptions(builder, dataFile.getUpdate());
        applyPerformanceOptions(builder, performanceConfig);
        return builder;
    }

    protected DeviceDetectionOnPremisePipelineBuilder makeRawBuilder(DataFile dataFile) throws Exception {
        final Boolean shouldMakeDataCopy = dataFile.getMakeTempCopy();
        return new DeviceDetectionPipelineBuilder()
                .useOnPremise(dataFile.getPath(), shouldMakeDataCopy != null && shouldMakeDataCopy);
    }

    private static final MergingConfigurator<DeviceDetectionOnPremisePipelineBuilder, PerformanceConfig>
            PERFORMANCE_CONFIG_MERGER = new MergingConfigurator<>(List.of(
            new PropertyMerge<>(PerformanceConfig::getProfile, s -> !s.isEmpty(), (pipelineBuilder, profile) -> {
                final String lowercasedProfile = profile.toLowerCase();
                for (Constants.PerformanceProfiles nextProfile: Constants.PerformanceProfiles.values()) {
                    if (nextProfile.name().toLowerCase().equals(lowercasedProfile)) {
                        pipelineBuilder.setPerformanceProfile(nextProfile);
                        return;
                    }
                }
            }),
            new PropertyMerge<>(PerformanceConfig::getConcurrency, v -> true, DeviceDetectionOnPremisePipelineBuilder::setConcurrency),
            new PropertyMerge<>(PerformanceConfig::getDifference, v -> true, DeviceDetectionOnPremisePipelineBuilder::setDifference),
            new PropertyMerge<>(PerformanceConfig::getAllowUnmatched, v -> true, DeviceDetectionOnPremisePipelineBuilder::setAllowUnmatched),
            new PropertyMerge<>(PerformanceConfig::getDrift, v -> true, DeviceDetectionOnPremisePipelineBuilder::setDrift)));

    protected void applyPerformanceOptions(
            DeviceDetectionOnPremisePipelineBuilder pipelineBuilder,
            PerformanceConfig performanceConfig)
    {
        PERFORMANCE_CONFIG_MERGER.test(pipelineBuilder, performanceConfig);
    }

    private static final MergingConfigurator<DeviceDetectionOnPremisePipelineBuilder, DataFileUpdate> UPDATE_CONFIG_MERGER = new MergingConfigurator<>(List.of(
            new PropertyMerge<>(DataFileUpdate::getAuto, v -> true, DeviceDetectionOnPremisePipelineBuilder::setAutoUpdate),
            new PropertyMerge<>(DataFileUpdate::getOnStartup, v -> true, DeviceDetectionOnPremisePipelineBuilder::setDataUpdateOnStartup),
            new PropertyMerge<>(DataFileUpdate::getUrl, s -> !s.isEmpty(), DeviceDetectionOnPremisePipelineBuilder::setDataUpdateUrl),
            new PropertyMerge<>(DataFileUpdate::getLicenseKey, s -> !s.isEmpty(), DeviceDetectionOnPremisePipelineBuilder::setDataUpdateLicenseKey),
            new PropertyMerge<>(DataFileUpdate::getWatchFileSystem, v -> true, DeviceDetectionOnPremisePipelineBuilder::setDataFileSystemWatcher),
            new PropertyMerge<>(DataFileUpdate::getPollingInterval, v -> true, DeviceDetectionOnPremisePipelineBuilder::setUpdatePollingInterval)));

    protected void applyUpdateOptions(DeviceDetectionOnPremisePipelineBuilder pipelineBuilder, DataFileUpdate updateConfig) {
        pipelineBuilder.setDataUpdateService(new DataUpdateServiceDefault());
        UPDATE_CONFIG_MERGER.test(pipelineBuilder, updateConfig);
    }

    @Override
    public Pipeline get() {
        return pipeline;
    }
}
