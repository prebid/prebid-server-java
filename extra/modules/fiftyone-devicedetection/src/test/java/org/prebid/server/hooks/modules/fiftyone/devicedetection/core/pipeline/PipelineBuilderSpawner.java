package org.prebid.server.hooks.modules.fiftyone.devicedetection.core.pipeline;

import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.DataFile;

/**
 * Given {@link DataFile} options returns a builder
 * capable of eventually producing {@link fiftyone.pipeline.core.flowelements.Pipeline}.
 * @param <T> Type of the builder returned.
 */
@FunctionalInterface
public interface PipelineBuilderSpawner<T> {
    /**
     * @param dataFile Config fragment with file location and some options.
     * @return Builder capable of eventually producing {@link fiftyone.pipeline.core.flowelements.Pipeline}.
     * @throws Exception Underlying error when constructing the builder.
     */
    T makeBuilder(DataFile dataFile) throws Exception;
}
