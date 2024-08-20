package org.prebid.server.hooks.modules.greenbids.real.time.data.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.ThrottlingThresholds;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.GreenbidsRealTimeDataProperties;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.OnnxModelRunner;
import org.prebid.server.hooks.modules.greenbids.real.time.data.v1.GreenbidsRealTimeDataModule;
import org.prebid.server.hooks.modules.greenbids.real.time.data.v1.GreenbidsRealTimeDataProcessedAuctionRequestHook;
import org.prebid.server.json.ObjectMapperProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@ConditionalOnProperty(prefix = "hooks." + GreenbidsRealTimeDataModule.CODE, name = "enabled", havingValue = "true")
@Configuration
public class GreenbidsRealTimeDataConfiguration {

    @Bean
    GreenbidsRealTimeDataModule greenbidsRealTimeDataModule(
            @Value("${hooks.modules.greenbids-real-time-data.google-cloud-greenbids-project}")
            String googleCloudGreenbidsProject,
            @Value("${hooks.modules.greenbids-real-time-data.gcs-bucket-name}") String gcsBucketName,
            @Value("${hooks.modules.greenbids-real-time-data.cache-expiration-minutes}") Integer cacheExpirationMinutes,
            @Value("${hooks.modules.greenbids-real-time-data.geo-lite-country-path}") String geoLiteCountryPath) {
        final ObjectMapper mapper = ObjectMapperProvider.mapper();

        final Cache<String, OnnxModelRunner> modelCacheWithExpiration = Caffeine.newBuilder()
                .expireAfterWrite(cacheExpirationMinutes, TimeUnit.MINUTES)
                .build();

        final Cache<String, ThrottlingThresholds> thresholdsCacheWithExpiration = Caffeine.newBuilder()
                .expireAfterWrite(cacheExpirationMinutes, TimeUnit.MINUTES)
                .build();

        final GreenbidsRealTimeDataProperties globalProperties = GreenbidsRealTimeDataProperties.of(
                modelCacheWithExpiration,
                thresholdsCacheWithExpiration,
                geoLiteCountryPath,
                googleCloudGreenbidsProject,
                gcsBucketName,
                cacheExpirationMinutes
        );

        return new GreenbidsRealTimeDataModule(List.of(
                new GreenbidsRealTimeDataProcessedAuctionRequestHook(
                        mapper,
                        modelCacheWithExpiration,
                        thresholdsCacheWithExpiration,
                        geoLiteCountryPath,
                        googleCloudGreenbidsProject,
                        gcsBucketName)));
    }
}
