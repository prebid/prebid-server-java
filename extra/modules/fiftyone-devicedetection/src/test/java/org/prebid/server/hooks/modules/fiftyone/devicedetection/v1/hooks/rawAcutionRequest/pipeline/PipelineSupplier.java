package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks.rawAcutionRequest.pipeline;

import fiftyone.pipeline.core.flowelements.Pipeline;

@FunctionalInterface
public interface PipelineSupplier {
    Pipeline get() throws Exception;
}
