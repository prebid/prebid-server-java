package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.config;

import lombok.Data;

@Data
public final class PerformanceConfig {
    String profile;
    Integer concurrency;
    Integer difference;
    Boolean allowUnmatched;
    Integer drift;
}
