package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.config;

import com.scientiamobile.wurfl.core.WURFLEngine;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.model.WURFLEngineInitializer;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.v1.WURFLDeviceDetectionEntrypointHook;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.v1.WURFLDeviceDetectionModule;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.v1.WURFLDeviceDetectionRawAuctionRequestHook;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.v1.WURFLService;
import org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.config.WURFLDeviceDetectionConfigProperties;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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

    private static final Long DAILY_SYNC_INTERVAL = 86400000L;

    @Bean
    @ConfigurationProperties(prefix = "hooks.modules." + WURFLDeviceDetectionModule.CODE)
    WURFLDeviceDetectionConfigProperties configProperties() {
        return new WURFLDeviceDetectionConfigProperties();
    }

    @Bean
    public WURFLDeviceDetectionModule wurflDeviceDetectionModule(WURFLDeviceDetectionConfigProperties
                                                                         configProperties, Vertx vertx) {

        final WURFLEngine wurflEngine = WURFLEngineInitializer.builder()
                .configProperties(configProperties)
                .build().initWURFLEngine();
        wurflEngine.load();

        final WURFLService wurflService = new WURFLService(wurflEngine, configProperties);

        if (configProperties.isWurflRunUpdater()) {
            final FileSyncer fileSyncer = createFileSyncer(configProperties, wurflService, vertx);
            // Update process via file syncer starts with a delay because wurfl file has just been downloaded
            vertx.setTimer(DAILY_SYNC_INTERVAL, ignored -> fileSyncer.sync());
        }

        return new WURFLDeviceDetectionModule(List.of(new WURFLDeviceDetectionEntrypointHook(),
                new WURFLDeviceDetectionRawAuctionRequestHook(wurflService, configProperties)));
    }

    private FileSyncer createFileSyncer(WURFLDeviceDetectionConfigProperties configProperties,
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
        fileSyncerProperties.setDownloadUrl(configProperties.getWurflSnapshotUrl());
        fileSyncerProperties.setSaveFilepath(downloadPath);
        fileSyncerProperties.setTmpFilepath(tempPath);
        fileSyncerProperties.setTimeoutMs((long) configProperties.getUpdateConnTimeoutMs());
        fileSyncerProperties.setUpdateIntervalMs(DAILY_SYNC_INTERVAL);
        fileSyncerProperties.setRetryCount(configProperties.getUpdateRetries());
        fileSyncerProperties.setRetryIntervalMs(configProperties.getRetryIntervalMs());
        fileSyncerProperties.setHttpClient(httpProperties);

        return fileSyncerProperties;
    }

    private String createDownloadPath(WURFLDeviceDetectionConfigProperties configProperties) {
        final String basePath = configProperties.getWurflFileDirPath();
        final String fileName = configProperties.getWurflSnapshotUrl().endsWith(".xml.gz")
                ? "new_wurfl.xml.gz"
                : "new_wurfl.zip";
        return Path.of(basePath, fileName).toString();
    }

    private String createTempPath(WURFLDeviceDetectionConfigProperties configProperties) {
        final String basePath = configProperties.getWurflFileDirPath();
        final String fileName = configProperties.getWurflSnapshotUrl().endsWith(".xml.gz")
                ? "temp_wurfl.xml.gz"
                : "temp_wurfl.zip";
        return Path.of(basePath, fileName).toString();
    }

    private HttpClientProperties createHttpProperties(WURFLDeviceDetectionConfigProperties configProperties) {
        final HttpClientProperties httpProperties = new HttpClientProperties();
        httpProperties.setConnectTimeoutMs(configProperties.getUpdateConnTimeoutMs());
        httpProperties.setMaxRedirects(1);
        return httpProperties;
    }
}
