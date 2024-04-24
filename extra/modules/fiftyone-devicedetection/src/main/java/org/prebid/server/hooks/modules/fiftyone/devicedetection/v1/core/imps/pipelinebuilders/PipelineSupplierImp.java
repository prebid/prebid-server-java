package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps.pipelinebuilders;

import fiftyone.devicedetection.DeviceDetectionOnPremisePipelineBuilder;
import fiftyone.pipeline.core.flowelements.Pipeline;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.PipelineSupplier;

public final class PipelineSupplierImp implements PipelineSupplier {
    private final Pipeline pipeline;

    public PipelineSupplierImp(DeviceDetectionOnPremisePipelineBuilder builder) throws Exception {
        pipeline = builder.build();
    }

    @Override
    public Pipeline get() {
        return pipeline;
    }
}
