package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.config;

import lombok.Data;

@Data
public final class ModuleConfig {
    AccountFilter accountFilter;
    DataFile dataFile;
    PerformanceConfig performance;
}
