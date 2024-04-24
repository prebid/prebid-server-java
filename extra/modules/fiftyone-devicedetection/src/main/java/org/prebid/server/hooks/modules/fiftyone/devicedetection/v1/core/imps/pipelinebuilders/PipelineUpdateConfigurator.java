package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps.pipelinebuilders;

import fiftyone.devicedetection.DeviceDetectionOnPremisePipelineBuilder;
import fiftyone.pipeline.engines.services.DataUpdateServiceDefault;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps.mergers.MergingConfiguratorImp;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps.mergers.PropertyMergeImp;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.config.DataFileUpdate;

import java.util.List;

public final class PipelineUpdateConfigurator implements PipelineConfigurator<DataFileUpdate> {
    private static final MergingConfiguratorImp<DeviceDetectionOnPremisePipelineBuilder, DataFileUpdate> MERGER = new MergingConfiguratorImp<>(List.of(
            new PropertyMergeImp<>(DataFileUpdate::getAuto, v -> true, DeviceDetectionOnPremisePipelineBuilder::setAutoUpdate),
            new PropertyMergeImp<>(DataFileUpdate::getOnStartup, v -> true, DeviceDetectionOnPremisePipelineBuilder::setDataUpdateOnStartup),
            new PropertyMergeImp<>(DataFileUpdate::getUrl, s -> !s.isEmpty(), DeviceDetectionOnPremisePipelineBuilder::setDataUpdateUrl),
            new PropertyMergeImp<>(DataFileUpdate::getLicenseKey, s -> !s.isEmpty(), DeviceDetectionOnPremisePipelineBuilder::setDataUpdateLicenseKey),
            new PropertyMergeImp<>(DataFileUpdate::getWatchFileSystem, v -> true, DeviceDetectionOnPremisePipelineBuilder::setDataFileSystemWatcher),
            new PropertyMergeImp<>(DataFileUpdate::getPollingInterval, v -> true, DeviceDetectionOnPremisePipelineBuilder::setUpdatePollingInterval)));

    @Override
    public void accept(DeviceDetectionOnPremisePipelineBuilder pipelineBuilder, DataFileUpdate updateConfig) {
        pipelineBuilder.setDataUpdateService(new DataUpdateServiceDefault());
        MERGER.applyProperties(pipelineBuilder, updateConfig);
    }
}
