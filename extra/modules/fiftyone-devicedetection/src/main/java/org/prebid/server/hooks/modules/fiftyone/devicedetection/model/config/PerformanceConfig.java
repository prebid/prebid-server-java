package org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config;

import fiftyone.pipeline.engines.Constants;
import lombok.Data;

/**
 * Config fragment with performance settings.
 */
@Data
public final class PerformanceConfig {
    /**
     * Set the performance profile for the device detection engine.
     * @see fiftyone.devicedetection.DeviceDetectionOnPremisePipelineBuilder#setPerformanceProfile(Constants.PerformanceProfiles)
     */
    String profile;

    /**
     * Set the expected number of concurrent operations using the engine.
     * @see fiftyone.devicedetection.DeviceDetectionOnPremisePipelineBuilder#setConcurrency(int)
     */
    Integer concurrency;

    /**
     * Set the maximum difference to allow when processing HTTP headers.
     * @see fiftyone.devicedetection.DeviceDetectionOnPremisePipelineBuilder#setDifference(int)
     */
    Integer difference;

    /**
     * If set to false, a non-matching User-Agent will result in properties without set values.
     * @see fiftyone.devicedetection.DeviceDetectionOnPremisePipelineBuilder#setAllowUnmatched(boolean)
     */
    Boolean allowUnmatched;

    /**
     * Set the maximum drift to allow when matching hashes.
     * @see fiftyone.devicedetection.DeviceDetectionOnPremisePipelineBuilder#setDrift(int)
     */
    Integer drift;
}
