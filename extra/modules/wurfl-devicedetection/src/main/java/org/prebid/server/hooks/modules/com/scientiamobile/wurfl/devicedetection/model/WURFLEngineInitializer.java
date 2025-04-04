package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.model;

import com.scientiamobile.wurfl.core.GeneralWURFLEngine;
import com.scientiamobile.wurfl.core.WURFLEngine;
import com.scientiamobile.wurfl.core.cache.LRUMapCacheProvider;
import com.scientiamobile.wurfl.core.cache.NullCacheProvider;
import com.scientiamobile.wurfl.core.updater.Frequency;
import com.scientiamobile.wurfl.core.updater.WURFLUpdater;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.config.WURFLDeviceDetectionConfigProperties;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.exc.WURFLModuleConfigurationException;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Builder
public class WURFLEngineInitializer {

    private WURFLDeviceDetectionConfigProperties configProperties;

    public WURFLEngine initWURFLEngine() {
        downloadWurflFile(configProperties);
        final WURFLEngine engine = initializeEngine(configProperties);
        setupUpdater(configProperties, engine);
        return engine;
    }

    static void downloadWurflFile(WURFLDeviceDetectionConfigProperties configProperties) {
        if (StringUtils.isNotBlank(configProperties.getWurflSnapshotUrl())
                && StringUtils.isNotBlank(configProperties.getWurflFileDirPath())) {
            GeneralWURFLEngine.wurflDownload(
                    configProperties.getWurflSnapshotUrl(),
                    configProperties.getWurflFileDirPath());
        }
    }

    static WURFLEngine initializeEngine(WURFLDeviceDetectionConfigProperties configProperties) {

        final String wurflFileName = extractWURFLFileName(configProperties.getWurflSnapshotUrl());

        final Path wurflPath = Paths.get(
                configProperties.getWurflFileDirPath(),
                wurflFileName
        );
        final WURFLEngine engine = new GeneralWURFLEngine(wurflPath.toString());
        verifyStaticCapabilitiesDefinition(engine);

        if (configProperties.getCacheSize() > 0) {
            engine.setCacheProvider(new LRUMapCacheProvider(configProperties.getCacheSize()));
        } else {
            engine.setCacheProvider(new NullCacheProvider());
        }
        return engine;
    }

    private static String extractWURFLFileName(String wurflSnapshotUrl) {

        try {
            final URI uri = new URI(wurflSnapshotUrl);
            final String path = uri.getPath();
            return path.substring(path.lastIndexOf('/') + 1);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid WURFL snapshot URL: " + wurflSnapshotUrl, e);
        }
    }

    static void verifyStaticCapabilitiesDefinition(WURFLEngine engine) {

        final List<String> unsupportedStaticCaps = new ArrayList<>();
        final Map<String, Boolean> allCaps = engine.getAllCapabilities().stream()
                .collect(Collectors.toMap(
                        key -> key,
                        value -> true
                ));

        for (String requiredCapName : WURFLDeviceDetectionConfigProperties.REQUIRED_STATIC_CAPS) {
            if (!allCaps.containsKey(requiredCapName)) {
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

    static void setupUpdater(WURFLDeviceDetectionConfigProperties configProperties, WURFLEngine engine) {
        final boolean runUpdater = configProperties.isWurflRunUpdater();

        if (runUpdater) {
            final WURFLUpdater updater = new WURFLUpdater(engine, configProperties.getWurflSnapshotUrl());
            updater.setFrequency(Frequency.DAILY);
            updater.performPeriodicUpdate();
        }
    }
}
