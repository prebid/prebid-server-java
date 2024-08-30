package org.prebid.server.hooks.modules.greenbids.real.time.data.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.ThrottlingThresholds;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.config.GreenbidsRealTimeDataProperties;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.predictor.OnnxModelRunner;
import org.prebid.server.hooks.modules.greenbids.real.time.data.v1.GreenbidsRealTimeDataModule;
import org.prebid.server.hooks.modules.greenbids.real.time.data.v1.GreenbidsRealTimeDataProcessedAuctionRequestHook;
import org.prebid.server.json.ObjectMapperProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;

@ConditionalOnProperty(prefix = "hooks." + GreenbidsRealTimeDataModule.CODE, name = "enabled", havingValue = "true")
@Configuration
@EnableConfigurationProperties(GreenbidsRealTimeDataProperties.class)
public class GreenbidsRealTimeDataConfiguration {

    @Bean
    GreenbidsRealTimeDataModule greenbidsRealTimeDataModule(GreenbidsRealTimeDataProperties properties) {
        final ObjectMapper mapper = ObjectMapperProvider.mapper();

        final Storage storage = StorageOptions.newBuilder()
                .setProjectId(properties.getGoogleCloudGreenbidsProject()).build().getService();

        final File database = new File(properties.getGeoLiteCountryPath());

        final Cache<String, OnnxModelRunner> modelCacheWithExpiration = Caffeine.newBuilder()
                .expireAfterWrite(properties.getCacheExpirationMinutes(), TimeUnit.MINUTES)
                .build();

        final Cache<String, ThrottlingThresholds> thresholdsCacheWithExpiration = Caffeine.newBuilder()
                .expireAfterWrite(properties.getCacheExpirationMinutes(), TimeUnit.MINUTES)
                .build();

        return new GreenbidsRealTimeDataModule(List.of(
                new GreenbidsRealTimeDataProcessedAuctionRequestHook(
                        mapper,
                        modelCacheWithExpiration,
                        thresholdsCacheWithExpiration,
                        properties.getGcsBucketName(),
                        properties.getOnnxModelCacheKeyPrefix(),
                        properties.getThresholdsCacheKeyPrefix(),
                        storage,
                        database)));
    }
}
