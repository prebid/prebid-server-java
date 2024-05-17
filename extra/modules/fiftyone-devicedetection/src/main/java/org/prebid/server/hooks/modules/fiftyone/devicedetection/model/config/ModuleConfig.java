package org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config;

import lombok.Data;

/**
 * Root element of module config.
 */
@Data
public final class ModuleConfig {
    /** Config fragment to restrict module usage by requesting accounts. */
    AccountFilter accountFilter;

    /** Config fragment on data file and its update settings. */
    DataFile dataFile;

    /** Config fragment with performance settings. */
    PerformanceConfig performance;
}
