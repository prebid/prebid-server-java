package org.prebid.server.hooks.modules.greenbids.real.time.data.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import io.vertx.core.Vertx;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.filter.ThrottlingThresholds;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.ThrottlingThresholdsFactory;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.GreenbidsInferenceDataService;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.FilterService;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.ModelCache;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.OnnxModelRunner;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.OnnxModelRunnerFactory;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.OnnxModelRunnerWithThresholds;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.ThresholdCache;
import org.prebid.server.hooks.modules.greenbids.real.time.data.core.GreenbidsInvocationService;
import org.prebid.server.hooks.modules.greenbids.real.time.data.v1.GreenbidsRealTimeDataProcessedAuctionRequestHook;
import org.prebid.server.json.ObjectMapperProvider;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

@ConditionalOnProperty(prefix = "hooks." + GreenbidsRealTimeDataModule.CODE, name = "enabled", havingValue = "true")
@Configuration
@EnableConfigurationProperties(GreenbidsRealTimeDataProperties.class)
public class GreenbidsRealTimeDataConfiguration {

    @Bean
    DatabaseReaderFactory databaseReaderFactory(
            GreenbidsRealTimeDataProperties properties, Vertx vertx, HttpClient httpClient) {
        return new DatabaseReaderFactory(properties, vertx, httpClient);
    }

    @Bean
    GreenbidsInferenceDataService greenbidsInferenceDataService(DatabaseReaderFactory databaseReaderFactory) {
        return new GreenbidsInferenceDataService(
                databaseReaderFactory, ObjectMapperProvider.mapper());
    }

    @Bean
    GreenbidsRealTimeDataModule greenbidsRealTimeDataModule(
            FilterService filterService,
            OnnxModelRunnerWithThresholds onnxModelRunnerWithThresholds,
            GreenbidsInferenceDataService greenbidsInferenceDataService,
            GreenbidsInvocationService greenbidsInvocationService) {

        return new GreenbidsRealTimeDataModule(List.of(
                new GreenbidsRealTimeDataProcessedAuctionRequestHook(
                        ObjectMapperProvider.mapper(),
                        filterService,
                        onnxModelRunnerWithThresholds,
                        greenbidsInferenceDataService,
                        greenbidsInvocationService)));
    }

    @Bean
    FilterService filterService() {
        return new FilterService();
    }

    @Bean
    Storage storage(GreenbidsRealTimeDataProperties properties) {
        return StorageOptions.newBuilder()
                .setProjectId(properties.getGoogleCloudGreenbidsProject()).build().getService();
    }

    @Bean
    OnnxModelRunnerFactory onnxModelRunnerFactory() {
        return new OnnxModelRunnerFactory();
    }

    @Bean
    ThrottlingThresholdsFactory throttlingThresholdsFactory() {
        return new ThrottlingThresholdsFactory();
    }

    @Bean
    ModelCache modelCache(
            GreenbidsRealTimeDataProperties properties,
            Vertx vertx,
            Storage storage,
            OnnxModelRunnerFactory onnxModelRunnerFactory) {

        final Cache<String, OnnxModelRunner> modelCacheWithExpiration = Caffeine.newBuilder()
                .expireAfterWrite(properties.getCacheExpirationMinutes(), TimeUnit.MINUTES)
                .build();

        return new ModelCache(
                storage,
                properties.getGcsBucketName(),
                modelCacheWithExpiration,
                properties.getOnnxModelCacheKeyPrefix(),
                vertx,
                onnxModelRunnerFactory);
    }

    @Bean
    ThresholdCache thresholdCache(
            GreenbidsRealTimeDataProperties properties,
            Vertx vertx,
            Storage storage,
            ThrottlingThresholdsFactory throttlingThresholdsFactory) {

        final Cache<String, ThrottlingThresholds> thresholdsCacheWithExpiration = Caffeine.newBuilder()
                .expireAfterWrite(properties.getCacheExpirationMinutes(), TimeUnit.MINUTES)
                .build();

        return new ThresholdCache(
                storage,
                properties.getGcsBucketName(),
                ObjectMapperProvider.mapper(),
                thresholdsCacheWithExpiration,
                properties.getThresholdsCacheKeyPrefix(),
                vertx,
                throttlingThresholdsFactory);
    }

    @Bean
    OnnxModelRunnerWithThresholds onnxModelRunnerWithThresholds(
            ModelCache modelCache,
            ThresholdCache thresholdCache) {

        return new OnnxModelRunnerWithThresholds(modelCache, thresholdCache);
    }

    @Bean
    GreenbidsInvocationService greenbidsInvocationService() {
        return new GreenbidsInvocationService();
    }
}
