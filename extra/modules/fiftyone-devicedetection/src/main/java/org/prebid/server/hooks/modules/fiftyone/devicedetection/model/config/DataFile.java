package org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config;

import lombok.Data;

/**
 * Config fragment on data file and its update settings.
 */
@Data
public final class DataFile {
    /** 
     * The full path to the device detection data file.
     * @see fiftyone.devicedetection.DeviceDetectionPipelineBuilder#useOnPremise(String, boolean)
     */
    String path;

    /**
     * If true, the engine will create a temporary copy of the data file rather than using the data file directly.
     * @see fiftyone.devicedetection.DeviceDetectionPipelineBuilder#useOnPremise(String, boolean)
     */
    Boolean makeTempCopy;

    /** Update settings. */
    DataFileUpdate update;
}
