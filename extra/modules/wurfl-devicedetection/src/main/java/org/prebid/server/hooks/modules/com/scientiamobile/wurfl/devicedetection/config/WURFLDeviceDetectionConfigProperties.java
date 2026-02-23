package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.config;

import lombok.Data;

import java.util.Collections;
import java.util.Set;

@Data
public class WURFLDeviceDetectionConfigProperties {

    private static final int DEFAULT_UPDATE_TIMEOUT = 5000;
    private static final long DEFAULT_RETRY_INTERVAL = 200L;
    private static final int DEFAULT_UPDATE_RETRIES = 3;

    int cacheSize;

    String fileDirPath;

    String fileSnapshotUrl;

    boolean extCaps;

    int updateFrequencyInHours;

    Set<String> allowedPublisherIds = Collections.emptySet();

    int updateConnTimeoutMs = DEFAULT_UPDATE_TIMEOUT;

    int updateRetries = DEFAULT_UPDATE_RETRIES;

    long retryIntervalMs = DEFAULT_RETRY_INTERVAL;
}
