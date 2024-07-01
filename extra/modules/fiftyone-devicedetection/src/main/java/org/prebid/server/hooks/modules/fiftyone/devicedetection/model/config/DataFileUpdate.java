package org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config;

import lombok.Data;

@Data
public final class DataFileUpdate {
    Boolean auto;
    Boolean onStartup;
    String url;
    String licenseKey;
    Boolean watchFileSystem;
    Integer pollingInterval;
}
