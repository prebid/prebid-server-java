package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.config;

import lombok.Data;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.v1.WURFLDeviceDetectionModule;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Set;

@ConfigurationProperties(prefix = "hooks.modules." + WURFLDeviceDetectionModule.CODE)
@Data

public class WURFLDeviceDetectionConfigProperties {

    private static final int DEFAULT_UPDATE_TIMEOUT = 5000;
    private static final long DEFAULT_RETRY_INTERVAL = 200L;
    private static final int DEFAULT_UPDATE_RETRIES = 3;

    public static final Set<String> REQUIRED_STATIC_CAPS = Set.of(
            "ajax_support_javascript",
            "brand_name",
            "density_class",
            "is_connected_tv",
            "is_ott",
            "is_tablet",
            "model_name",
            "resolution_height",
            "resolution_width",
            "physical_form_factor"
    );

    public static final Set<String> REQUIRED_VIRTUAL_CAPS = Set.of(

            "advertised_device_os",
            "advertised_device_os_version",
            "complete_device_name",
            "is_full_desktop",
            "is_mobile",
            "is_phone",
            "form_factor",
            "pixel_density"
    );

    int cacheSize;

    String wurflFileDirPath;

    String wurflSnapshotUrl;

    boolean extCaps;

    boolean wurflRunUpdater = true;

    List<String> allowedPublisherIds = List.of();

    int updateConnTimeoutMs = DEFAULT_UPDATE_TIMEOUT;

    int updateRetries = DEFAULT_UPDATE_RETRIES;

    long retryIntervalMs = DEFAULT_RETRY_INTERVAL;
}
