package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core;

import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.config.DataFile;

@FunctionalInterface
public interface PipelineBuilderSpawner<T> {
    T makeBuilder(DataFile dataFile) throws Exception;
}
