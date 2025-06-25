package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.model;

import com.scientiamobile.wurfl.core.GeneralWURFLEngine;
import com.scientiamobile.wurfl.core.WURFLEngine;
import com.scientiamobile.wurfl.core.cache.LRUMapCacheProvider;
import com.scientiamobile.wurfl.core.cache.NullCacheProvider;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.config.WURFLDeviceDetectionConfigProperties;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.exc.WURFLModuleConfigurationException;

import java.net.URI;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class WURFLEngineUtils {

    private WURFLEngineUtils() {
    }

    private static final Set<String> REQUIRED_STATIC_CAPS = Set.of(
            "ajax_support_javascript",
            "brand_name",
            "density_class",
            "is_connected_tv",
            "is_ott",
            "is_tablet",
            "model_name",
            "resolution_height",
            "resolution_width",
            "physical_form_factor");

    public static WURFLEngine initializeEngine(WURFLDeviceDetectionConfigProperties configProperties,
                                               String wurflInFilePath) {
        final String wurflFilePath = wurflInFilePath != null
                ? wurflInFilePath
                : wurflFilePathFromConfig(configProperties);

        final WURFLEngine engine = new GeneralWURFLEngine(wurflFilePath);
        verifyStaticCapabilitiesDefinition(engine);

        final int cacheSize = configProperties.getCacheSize();
        engine.setCacheProvider(cacheSize > 0
                ? new LRUMapCacheProvider(configProperties.getCacheSize())
                : new NullCacheProvider());

        return engine;
    }

    private static String wurflFilePathFromConfig(WURFLDeviceDetectionConfigProperties configProperties) {
        final String wurflFileName = extractWURFLFileName(configProperties.getFileSnapshotUrl());
        return Paths.get(configProperties.getFileDirPath(), wurflFileName).toAbsolutePath().toString();
    }

    public static String extractWURFLFileName(String wurflSnapshotUrl) {
        try {
            final URI uri = new URI(wurflSnapshotUrl);
            final String path = uri.getPath();
            return path.substring(path.lastIndexOf('/') + 1);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid WURFL snapshot URL: " + wurflSnapshotUrl, e);
        }
    }

    private static void verifyStaticCapabilitiesDefinition(WURFLEngine engine) {
        final List<String> unsupportedStaticCaps = new ArrayList<>();
        for (String requiredCapName : REQUIRED_STATIC_CAPS) {
            if (!engine.getAllCapabilities().contains(requiredCapName)) {
                unsupportedStaticCaps.add(requiredCapName);
            }
        }

        if (!unsupportedStaticCaps.isEmpty()) {
            Collections.sort(unsupportedStaticCaps);
            final String failedCheckMessage = """
                                Static capabilities  %s needed for device enrichment are not defined in WURFL.
                                Please make sure that your license has the needed capabilities or upgrade it.
                    """.formatted(String.join(",", unsupportedStaticCaps));

            throw new WURFLModuleConfigurationException(failedCheckMessage);
        }
    }
}
