package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core;

import fiftyone.devicedetection.DeviceDetectionOnPremisePipelineBuilder;
import fiftyone.devicedetection.DeviceDetectionPipelineBuilder;
import fiftyone.pipeline.core.flowelements.Pipeline;
import fiftyone.pipeline.engines.Constants;
import fiftyone.pipeline.engines.services.DataUpdateServiceDefault;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.DataFile;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.DataFileUpdate;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.ModuleConfig;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.PerformanceConfig;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

public class PipelineBuilder {
    private static final Collection<String> PROPERTIES_USED = List.of(
            "devicetype",
            "hardwarevendor",
            "hardwaremodel",
            "hardwarename",
            "platformname",
            "platformversion",
            "screenpixelsheight",
            "screenpixelswidth",
            "screeninchesheight",
            "pixelratio",

            "BrowserName",
            "BrowserVersion",
            "IsCrawler",

            "BrowserVendor",
            "PlatformVendor",
            "Javascript",
            "GeoLocation",
            "HardwareModelVariants");

    private final ModuleConfig moduleConfig;

    public PipelineBuilder(ModuleConfig moduleConfig) {
        this.moduleConfig = moduleConfig;
    }

    public Pipeline build(DeviceDetectionPipelineBuilder premadeBuilder) throws Exception {
        final DataFile dataFile = moduleConfig.getDataFile();

        final Boolean shouldMakeDataCopy = dataFile.getMakeTempCopy();
        final DeviceDetectionOnPremisePipelineBuilder builder = premadeBuilder.useOnPremise(
                dataFile.getPath(),
                BooleanUtils.isTrue(shouldMakeDataCopy));

        applyUpdateOptions(builder, dataFile.getUpdate());
        applyPerformanceOptions(builder, moduleConfig.getPerformance());
        PROPERTIES_USED.forEach(builder::setProperty);
        return builder.build();
    }

    private static void applyUpdateOptions(DeviceDetectionOnPremisePipelineBuilder pipelineBuilder,
                                           DataFileUpdate updateConfig) {
        if (updateConfig == null) {
            return;
        }
        pipelineBuilder.setDataUpdateService(new DataUpdateServiceDefault());

        final Boolean auto = updateConfig.getAuto();
        if (auto != null) {
            pipelineBuilder.setAutoUpdate(auto);
        }

        final Boolean onStartup = updateConfig.getOnStartup();
        if (onStartup != null) {
            pipelineBuilder.setDataUpdateOnStartup(onStartup);
        }

        final String url = updateConfig.getUrl();
        if (StringUtils.isNotEmpty(url)) {
            pipelineBuilder.setDataUpdateUrl(url);
        }

        final String licenseKey = updateConfig.getLicenseKey();
        if (StringUtils.isNotEmpty(licenseKey)) {
            pipelineBuilder.setDataUpdateLicenseKey(licenseKey);
        }

        final Boolean watchFileSystem = updateConfig.getWatchFileSystem();
        if (watchFileSystem != null) {
            pipelineBuilder.setDataFileSystemWatcher(watchFileSystem);
        }

        final Integer pollingInterval = updateConfig.getPollingInterval();
        if (pollingInterval != null) {
            pipelineBuilder.setUpdatePollingInterval(pollingInterval);
        }
    }

    private static void applyPerformanceOptions(DeviceDetectionOnPremisePipelineBuilder pipelineBuilder,
                                                PerformanceConfig performanceConfig) {
        if (performanceConfig == null) {
            return;
        }
        final String profile = performanceConfig.getProfile();
        if (StringUtils.isNotEmpty(profile)) {
            setPerformanceProfile(pipelineBuilder, profile);
        }

        final Integer concurrency = performanceConfig.getConcurrency();
        if (concurrency != null) {
            pipelineBuilder.setConcurrency(concurrency);
        }

        final Integer difference = performanceConfig.getDifference();
        if (difference != null) {
            pipelineBuilder.setDifference(difference);
        }

        final Boolean allowUnmatched = performanceConfig.getAllowUnmatched();
        if (allowUnmatched != null) {
            pipelineBuilder.setAllowUnmatched(allowUnmatched);
        }

        final Integer drift = performanceConfig.getDrift();
        if (drift != null) {
            pipelineBuilder.setDrift(drift);
        }
    }

    private static void setPerformanceProfile(
            DeviceDetectionOnPremisePipelineBuilder pipelineBuilder,
            String profile) {
        for (Constants.PerformanceProfiles nextProfile : Constants.PerformanceProfiles.values()) {
            if (StringUtils.equalsIgnoreCase(nextProfile.name(), profile)) {
                pipelineBuilder.setPerformanceProfile(nextProfile);
                return;
            }
        }
        throw new IllegalArgumentException(
                "Invalid value for performance profile ("
                        + profile
                        + ") -- should be one of: "
                        + Arrays.stream(Constants.PerformanceProfiles.values())
                        .map(Enum::name)
                        .collect(Collectors.joining(", "))
        );
    }
}
