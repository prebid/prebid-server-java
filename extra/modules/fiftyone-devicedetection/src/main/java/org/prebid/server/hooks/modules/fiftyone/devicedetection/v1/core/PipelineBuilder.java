package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core;

import fiftyone.devicedetection.DeviceDetectionOnPremisePipelineBuilder;
import fiftyone.devicedetection.DeviceDetectionPipelineBuilder;
import fiftyone.pipeline.core.flowelements.Pipeline;
import fiftyone.pipeline.core.flowelements.PipelineBuilderBase;
import fiftyone.pipeline.engines.Constants;
import fiftyone.pipeline.engines.services.DataUpdateServiceDefault;

import jakarta.annotation.Nullable;

import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.DataFile;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.DataFileUpdate;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.ModuleConfig;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.PerformanceConfig;

import java.util.Collection;
import java.util.List;

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

    private final DeviceDetectionOnPremisePipelineBuilder premadeBuilder;

    public PipelineBuilder(@Nullable DeviceDetectionOnPremisePipelineBuilder premadeBuilder) {
        this.premadeBuilder = premadeBuilder;
    }

    public PipelineBuilderBase<?> build(ModuleConfig moduleConfig) throws Exception {
        final DataFile dataFile = moduleConfig.getDataFile();
        final DeviceDetectionOnPremisePipelineBuilder builder = makeRawBuilder(dataFile);
        applyUpdateOptions(builder, dataFile.getUpdate());
        applyPerformanceOptions(builder, moduleConfig.getPerformance());
        PROPERTIES_USED.forEach(builder::setProperty);
        return builder;
    }

    private DeviceDetectionOnPremisePipelineBuilder makeRawBuilder(DataFile dataFile) throws Exception {
        if (premadeBuilder != null) {
            return premadeBuilder;
        }
        final Boolean shouldMakeDataCopy = dataFile.getMakeTempCopy();
        return new DeviceDetectionPipelineBuilder()
                .useOnPremise(dataFile.getPath(), BooleanUtils.isTrue(shouldMakeDataCopy));
    }

    private void applyUpdateOptions(DeviceDetectionOnPremisePipelineBuilder pipelineBuilder,
                                           DataFileUpdate updateConfig) {
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
        if (StringUtils.isNotBlank(url)) {
            pipelineBuilder.setDataUpdateUrl(url);
        }

        final String licenseKey = updateConfig.getLicenseKey();
        if (StringUtils.isNotBlank(licenseKey)) {
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

    private void applyPerformanceOptions(DeviceDetectionOnPremisePipelineBuilder pipelineBuilder,
                                                PerformanceConfig performanceConfig) {
        final String profile = performanceConfig.getProfile();
        if (StringUtils.isNotBlank(profile)) {
            for (Constants.PerformanceProfiles nextProfile : Constants.PerformanceProfiles.values()) {
                if (StringUtils.equalsAnyIgnoreCase(nextProfile.name(), profile)) {
                    pipelineBuilder.setPerformanceProfile(nextProfile);
                    // todo: return or break the cycle?
                    return;
                }
            }
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
}
