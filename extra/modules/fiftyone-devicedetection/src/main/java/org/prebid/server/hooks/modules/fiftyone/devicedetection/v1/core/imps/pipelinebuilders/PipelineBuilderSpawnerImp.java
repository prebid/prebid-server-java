package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps.pipelinebuilders;

import fiftyone.devicedetection.DeviceDetectionOnPremisePipelineBuilder;
import fiftyone.devicedetection.DeviceDetectionPipelineBuilder;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.PipelineBuilderSpawner;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.config.DataFile;

public final class PipelineBuilderSpawnerImp
        implements PipelineBuilderSpawner<DeviceDetectionOnPremisePipelineBuilder> {

    @Override
    public DeviceDetectionOnPremisePipelineBuilder makeBuilder(DataFile dataFile) throws Exception {
        final Boolean shouldMakeDataCopy = dataFile.getMakeTempCopy();
        return new DeviceDetectionPipelineBuilder()
                .useOnPremise(dataFile.getPath(), shouldMakeDataCopy != null && shouldMakeDataCopy);
    }
}
