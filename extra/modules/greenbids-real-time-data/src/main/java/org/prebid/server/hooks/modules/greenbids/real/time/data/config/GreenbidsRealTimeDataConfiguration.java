package org.prebid.server.hooks.modules.greenbids.real.time.data.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.maxmind.geoip2.DatabaseReader;
import io.vertx.core.Vertx;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.ThrottlingThresholds;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.config.GreenbidsRealTimeDataProperties;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.data.GreenbidsInferenceDataService;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.predictor.FilterService;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.predictor.ModelCache;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.predictor.OnnxModelRunner;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.predictor.OnnxModelRunnerWithThresholds;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.predictor.ThresholdCache;
import org.prebid.server.hooks.modules.greenbids.real.time.data.v1.GreenbidsRealTimeDataModule;
import org.prebid.server.hooks.modules.greenbids.real.time.data.v1.GreenbidsRealTimeDataProcessedAuctionRequestHook;
import org.prebid.server.json.ObjectMapperProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

@ConditionalOnProperty(prefix = "hooks." + GreenbidsRealTimeDataModule.CODE, name = "enabled", havingValue = "true")
@Configuration
@EnableConfigurationProperties(GreenbidsRealTimeDataProperties.class)
public class GreenbidsRealTimeDataConfiguration {

    @Bean
    public GreenbidsInferenceDataService greenbidsInferenceDataService(
            GreenbidsRealTimeDataProperties properties,
            ObjectMapper mapper) {

        final File database = new File(properties.getGeoLiteCountryPath());
        DatabaseReader dbReader;
        try {
            dbReader = new DatabaseReader.Builder(database).build();
        } catch (IOException e) {
            throw new PreBidException("Failed build DatabaseReader from DB", e);
        }
        return new GreenbidsInferenceDataService(dbReader, mapper);
    }

    @Bean
    GreenbidsRealTimeDataModule greenbidsRealTimeDataModule(
            FilterService filterService,
            OnnxModelRunnerWithThresholds onnxModelRunnerWithThresholds,
            GreenbidsInferenceDataService greenbidsInferenceDataService,
            ObjectMapper mapper) {

        return new GreenbidsRealTimeDataModule(List.of(
                new GreenbidsRealTimeDataProcessedAuctionRequestHook(
                        mapper,
                        filterService,
                        onnxModelRunnerWithThresholds,
                        greenbidsInferenceDataService)));
    }

    @Bean
    public FilterService filterService() {
        return new FilterService();
    }

    @Bean
    public Storage storage(GreenbidsRealTimeDataProperties properties) {
        return StorageOptions.newBuilder()
                .setProjectId(properties.getGoogleCloudGreenbidsProject()).build().getService();
    }

    @Bean
    public ModelCache modelCache(GreenbidsRealTimeDataProperties properties, Vertx vertx, Storage storage) {
        final Cache<String, OnnxModelRunner> modelCacheWithExpiration = Caffeine.newBuilder()
                .expireAfterWrite(properties.getCacheExpirationMinutes(), TimeUnit.MINUTES)
                .build();

        return new ModelCache(
                storage,
                properties.getGcsBucketName(),
                modelCacheWithExpiration,
                properties.getOnnxModelCacheKeyPrefix(),
                vertx
        );
    }

    @Bean
    public ThresholdCache thresholdCache(
            GreenbidsRealTimeDataProperties properties,
            Vertx vertx,
            Storage storage,
            ObjectMapper mapper) {

        final Cache<String, ThrottlingThresholds> thresholdsCacheWithExpiration = Caffeine.newBuilder()
                .expireAfterWrite(properties.getCacheExpirationMinutes(), TimeUnit.MINUTES)
                .build();

        return new ThresholdCache(
                storage,
                properties.getGcsBucketName(),
                mapper,
                thresholdsCacheWithExpiration,
                properties.getThresholdsCacheKeyPrefix(),
                vertx);
    }

    @Bean
    public OnnxModelRunnerWithThresholds onnxModelRunnerWithThresholds(
            ModelCache modelCache,
            ThresholdCache thresholdCache) {

        return new OnnxModelRunnerWithThresholds(modelCache, thresholdCache);
    }
}
