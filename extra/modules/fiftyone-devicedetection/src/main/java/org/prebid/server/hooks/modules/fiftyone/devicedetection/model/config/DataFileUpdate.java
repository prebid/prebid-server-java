package org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config;

import lombok.Data;

/**
 * Config fragment on data file update.
 */
@Data
public final class DataFileUpdate {
    /**
     * Enable/Disable auto update.
     * @see fiftyone.devicedetection.DeviceDetectionOnPremisePipelineBuilder#setAutoUpdate(boolean)
     */
    Boolean auto;

    /**
     * Enable/Disable update on startup.
     * @see fiftyone.devicedetection.DeviceDetectionOnPremisePipelineBuilder#setDataUpdateOnStartup(boolean)
     **/
    Boolean onStartup;

    /**
     * Configure the engine to use the specified URL when looking for an updated data file.
     * @see fiftyone.devicedetection.DeviceDetectionOnPremisePipelineBuilder#setDataUpdateUrl(String)
     * */
    String url;

    /**
     * Set the license key used when checking for new device detection data files.
     * @see fiftyone.devicedetection.DeviceDetectionOnPremisePipelineBuilder#setDataUpdateLicenseKey(String)
     */
    String licenseKey;

    /**
     * The DataUpdateService has the ability to watch a file on disk and
     * refresh the engine as soon as that file is updated.
     * @see fiftyone.devicedetection.DeviceDetectionOnPremisePipelineBuilder#setDataFileSystemWatcher(boolean)
     */
    Boolean watchFileSystem;

    /**
     * Set the time between checks for a new data file made by the DataUpdateService in seconds.
     * @see fiftyone.devicedetection.DeviceDetectionOnPremisePipelineBuilder#setUpdatePollingInterval(int)
     */
    Integer pollingInterval;
}
