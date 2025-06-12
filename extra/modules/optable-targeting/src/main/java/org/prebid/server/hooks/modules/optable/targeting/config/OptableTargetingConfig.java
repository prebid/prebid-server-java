package org.prebid.server.hooks.modules.optable.targeting.config;

import io.vertx.core.Vertx;
import org.prebid.server.auction.privacy.enforcement.mask.UserFpdActivityMask;
import org.prebid.server.cache.PbcStorageService;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.hooks.modules.optable.targeting.v1.OptableTargetingAuctionResponseHook;
import org.prebid.server.hooks.modules.optable.targeting.v1.OptableTargetingModule;
import org.prebid.server.hooks.modules.optable.targeting.v1.OptableTargetingProcessedAuctionRequestHook;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.Cache;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.ConfigResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.IdsMapper;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.OptableAttributesResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.OptableTargeting;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.QueryBuilder;
import org.prebid.server.hooks.modules.optable.targeting.v1.net.APIClient;
import org.prebid.server.hooks.modules.optable.targeting.v1.net.APIClientFactory;
import org.prebid.server.hooks.modules.optable.targeting.v1.net.APIClientImpl;
import org.prebid.server.hooks.modules.optable.targeting.v1.net.CachedAPIClient;
import org.prebid.server.hooks.modules.optable.targeting.v1.net.OptableHttpClientWrapper;
import org.prebid.server.hooks.modules.optable.targeting.v1.net.OptableResponseMapper;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.json.ObjectMapperProvider;
import org.prebid.server.spring.config.VertxContextScope;
import org.prebid.server.spring.config.model.HttpClientProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;

import java.util.List;

@ConditionalOnProperty(prefix = "hooks." + OptableTargetingModule.CODE, name = "enabled", havingValue = "true")
@Configuration
public class OptableTargetingConfig {

    @Bean
    @ConfigurationProperties(prefix = "hooks.modules." + OptableTargetingModule.CODE)
    OptableTargetingProperties properties() {
        return new OptableTargetingProperties();
    }

    @Bean
    IdsMapper queryParametersExtractor(@Value("${logging.sampling-rate:0.01}") double logSamplingRate) {
        return new IdsMapper(ObjectMapperProvider.mapper(), logSamplingRate);
    }

    @Bean
    QueryBuilder queryBuilder() {
        return new QueryBuilder();
    }

    @Bean
    OptableResponseMapper optableResponseParser(JacksonMapper mapper) {
        return new OptableResponseMapper(mapper);
    }

    @Bean
    @Scope(scopeName = VertxContextScope.NAME, proxyMode = ScopedProxyMode.TARGET_CLASS)
    @ConditionalOnProperty(prefix = "http-client.circuit-breaker", name = "enabled", havingValue = "false",
            matchIfMissing = true)
    OptableHttpClientWrapper optableHttpClient(Vertx vertx, HttpClientProperties httpClientProperties) {
        return new OptableHttpClientWrapper(vertx, httpClientProperties);
    }

    @Bean(name = "apiClient")
    APIClient apiClientImpl(OptableHttpClientWrapper httpClientWrapper,
                        @Value("${logging.sampling-rate:0.01}")
                        double logSamplingRate,
                        OptableTargetingProperties properties,
                        OptableResponseMapper responseParser) {

        return new APIClientImpl(
                properties.getApiEndpoint(),
                httpClientWrapper.getHttpClient(),
                logSamplingRate,
                responseParser);
    }

    @Bean(name = "cachedAPIClient")
    APIClient cachedApiClient(APIClientImpl apiClient, Cache cache) {
        return new CachedAPIClient(apiClient, cache);
    }

    @Bean
    APIClientFactory apiClientFactory(
            @Qualifier("apiClient")
            APIClient apiClient,
            @Qualifier("cachedAPIClient")
            APIClient cachedApiClient,
            @Value("${storage.pbc.enabled:false}")
            boolean storageEnabled,
            @Value("${cache.module.enabled:false}")
            boolean moduleCacheEnabled) {

        return new APIClientFactory(
                apiClient,
                cachedApiClient,
                storageEnabled && moduleCacheEnabled);
    }

    @Bean
    OptableAttributesResolver optableAttributesResolver() {
        return new OptableAttributesResolver();
    }

    @Bean
    Cache cache(PbcStorageService cacheService, OptableResponseMapper responseMapper) {
        return new Cache(cacheService, responseMapper);
    }

    @Bean
    OptableTargeting optableTargeting(IdsMapper parametersExtractor,
                                      QueryBuilder queryBuilder,
                                      APIClientFactory apiClientFactory) {

        return new OptableTargeting(
                parametersExtractor,
                queryBuilder,
                apiClientFactory);
    }

    @Bean
    ConfigResolver configResolver(JsonMerger jsonMerger, OptableTargetingProperties globalProperties) {
        return new ConfigResolver(ObjectMapperProvider.mapper(), jsonMerger, globalProperties);
    }

    @Bean
    OptableTargetingModule optableTargetingModule(ConfigResolver configResolver,
                                                  OptableTargeting optableTargeting,
                                                  OptableAttributesResolver optableAttributesResolver,
                                                  UserFpdActivityMask userFpdActivityMask) {

        return new OptableTargetingModule(List.of(
                new OptableTargetingProcessedAuctionRequestHook(
                        configResolver,
                        optableTargeting,
                        optableAttributesResolver,
                        userFpdActivityMask),
                new OptableTargetingAuctionResponseHook(
                        configResolver,
                        ObjectMapperProvider.mapper())));
    }
}
