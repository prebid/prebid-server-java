package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.config;

import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.model.WURFLEngineInitializer;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.v1.WURFLDeviceDetectionEntrypointHook;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.v1.WURFLDeviceDetectionModule;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.v1.WURFLDeviceDetectionRawAuctionRequestHook;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.v1.WURFLService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.vertx.core.Vertx;
import org.prebid.server.execution.file.syncer.FileSyncer;
import org.prebid.server.spring.config.model.FileSyncerProperties;
import org.prebid.server.spring.config.model.HttpClientProperties;
import org.prebid.server.execution.file.FileUtil;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.List;

@ConditionalOnProperty(prefix = "hooks." + WURFLDeviceDetectionModule.CODE, name = "enabled", havingValue = "true")
@Configuration
public class WURFLDeviceDetectionConfiguration {

    private static final Long HOUR_IN_MILLIS = 3600000L;
    private static final int DEFAULT_UPDATE_FREQ_IN_HOURS = 24;

    @Bean
    @ConfigurationProperties(prefix = "hooks.modules." + WURFLDeviceDetectionModule.CODE)
    WURFLDeviceDetectionConfigProperties configProperties() {
        return new WURFLDeviceDetectionConfigProperties();
    }

    @Bean
    public WURFLDeviceDetectionModule wurflDeviceDetectionModule(WURFLDeviceDetectionConfigProperties
                                                                         configProperties, Vertx vertx) {

        final WURFLService wurflService;
        wurflService = new WURFLService(null, configProperties);
        final FileSyncer fileSyncer = createFileSyncer(configProperties, wurflService, vertx);
        fileSyncer.sync();

        return new WURFLDeviceDetectionModule(List.of(new WURFLDeviceDetectionEntrypointHook(),
                new WURFLDeviceDetectionRawAuctionRequestHook(wurflService, configProperties)));
    }

    FileSyncer createFileSyncer(WURFLDeviceDetectionConfigProperties configProperties,
                                        WURFLService wurflService, Vertx vertx) {
        final FileSyncerProperties fileSyncerProperties = createFileSyncerProperties(configProperties);
        return FileUtil.fileSyncerFor(wurflService, fileSyncerProperties, vertx);
    }

    private FileSyncerProperties createFileSyncerProperties(WURFLDeviceDetectionConfigProperties configProperties) {
        final String downloadPath = createDownloadPath(configProperties);
        final String tempPath = createTempPath(configProperties);
        final HttpClientProperties httpProperties = createHttpProperties(configProperties);

        final FileSyncerProperties fileSyncerProperties = new FileSyncerProperties();
        fileSyncerProperties.setCheckSize(true);
        fileSyncerProperties.setDownloadUrl(configProperties.getFileSnapshotUrl());
        fileSyncerProperties.setSaveFilepath(downloadPath);
        fileSyncerProperties.setTmpFilepath(tempPath);
        fileSyncerProperties.setTimeoutMs((long) configProperties.getUpdateConnTimeoutMs());
        fileSyncerProperties.setRetryCount(configProperties.getUpdateRetries());
        fileSyncerProperties.setRetryIntervalMs(configProperties.getRetryIntervalMs());
        fileSyncerProperties.setHttpClient(httpProperties);
        int updateFreqInHours = configProperties.getUpdateFrequencyInHours();
        if (updateFreqInHours == 0) {
            updateFreqInHours = DEFAULT_UPDATE_FREQ_IN_HOURS;
        }
        final long syncIntervalMillis = updateFreqInHours * HOUR_IN_MILLIS;
        fileSyncerProperties.setUpdateIntervalMs(syncIntervalMillis);

        return fileSyncerProperties;
    }

    private String createTempPath(WURFLDeviceDetectionConfigProperties configProperties) {
        final String basePath = configProperties.getFileDirPath();
        String fileName = WURFLEngineInitializer.extractWURFLFileName(configProperties.getFileSnapshotUrl());
        fileName = "tmp_" + fileName;
        return Path.of(basePath, fileName).toString();
    }

    private String createDownloadPath(WURFLDeviceDetectionConfigProperties configProperties) {
        final String basePath = configProperties.getFileDirPath();
        final String fileName = WURFLEngineInitializer.extractWURFLFileName(configProperties.getFileSnapshotUrl());
        return Path.of(basePath, fileName).toString();
    }

    private HttpClientProperties createHttpProperties(WURFLDeviceDetectionConfigProperties configProperties) {
        final HttpClientProperties httpProperties = new HttpClientProperties();
        httpProperties.setConnectTimeoutMs(configProperties.getUpdateConnTimeoutMs());
        httpProperties.setMaxRedirects(1);
        return httpProperties;
    }
}
