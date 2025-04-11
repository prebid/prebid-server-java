package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.config;

import lombok.Data;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.v1.WURFLDeviceDetectionModule;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Set;

@ConfigurationProperties(prefix = "hooks.modules." + WURFLDeviceDetectionModule.CODE)
@Data

public class WURFLDeviceDetectionConfigProperties {

    private static final int DEFAULT_UPDATE_TIMEOUT = 5000;
    private static final long DEFAULT_RETRY_INTERVAL = 200L;
    private static final int DEFAULT_UPDATE_RETRIES = 3;

    int cacheSize;

    String wurflFileDirPath;

    String wurflSnapshotUrl;

    boolean extCaps;

    boolean wurflRunUpdater = true;

    Set<String> allowedPublisherIds = Set.of();

    int updateConnTimeoutMs = DEFAULT_UPDATE_TIMEOUT;

    int updateRetries = DEFAULT_UPDATE_RETRIES;

    long retryIntervalMs = DEFAULT_RETRY_INTERVAL;
}
