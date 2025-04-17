package org.prebid.server.hooks.modules.optable.targeting.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Vertx;
import org.prebid.server.cache.PbcStorageService;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.hooks.modules.optable.targeting.v1.OptableTargetingAuctionResponseHook;
import org.prebid.server.hooks.modules.optable.targeting.v1.OptableTargetingModule;
import org.prebid.server.hooks.modules.optable.targeting.v1.OptableTargetingProcessedAuctionRequestHook;
import org.prebid.server.hooks.modules.optable.targeting.v1.analytics.AnalyticTagsResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.Cache;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.IdsMapper;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.ConfigResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.OptableAttributesResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.OptableTargeting;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.PayloadResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.QueryBuilder;
import org.prebid.server.hooks.modules.optable.targeting.v1.net.APIClient;
import org.prebid.server.hooks.modules.optable.targeting.v1.net.OptableHttpClientWrapper;
import org.prebid.server.hooks.modules.optable.targeting.v1.net.OptableResponseMapper;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.ObjectMapperProvider;
import org.prebid.server.spring.config.VertxContextScope;
import org.prebid.server.spring.config.model.HttpClientProperties;
import org.prebid.server.util.HttpUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;

import java.util.List;
import java.util.Objects;

@ConditionalOnProperty(prefix = "hooks." + OptableTargetingModule.CODE, name = "enabled", havingValue = "true")
@Configuration
@EnableConfigurationProperties(OptableTargetingProperties.class)
public class OptableTargetingConfig {

    @Bean
    ObjectMapper objectMapper() {
        return ObjectMapperProvider.mapper();
    }

    @Bean
    AnalyticTagsResolver analyticTagsResolver(ObjectMapper objectMapper) {
        return new AnalyticTagsResolver(objectMapper);
    }

    @Bean
    IdsMapper queryParametersExtractor(ObjectMapper objectMapper) {
        return new IdsMapper(objectMapper);
    }

    @Bean
    PayloadResolver payloadResolver(ObjectMapper mapper) {
        return new PayloadResolver(mapper);
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

    @Bean
    APIClient apiClient(OptableHttpClientWrapper httpClientWrapper,
                        @Value("${logging.sampling-rate:0.01}")
                        double logSamplingRate,
                        OptableTargetingProperties properties,
                        OptableResponseMapper responseParser) {

        final String endpoint = HttpUtil.validateUrl(Objects.requireNonNull(properties.getApiEndpoint()));

        return new APIClient(endpoint, httpClientWrapper.getHttpClient(), logSamplingRate, responseParser);
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
                                      APIClient apiClient,
                                      Cache cache,
                                      @Value("${storage.pbc.enabled:false}")
                                      boolean storageEnabled,
                                      @Value("${cache.module.enabled:false}")
                                      boolean moduleCacheEnabled) {

        return new OptableTargeting(cache, parametersExtractor, queryBuilder, apiClient,
                storageEnabled && moduleCacheEnabled);
    }

    @Bean
    ConfigResolver configResolver(ObjectMapper mapper, OptableTargetingProperties globalProperties) {
        return new ConfigResolver(mapper, globalProperties);
    }

    @Bean
    OptableTargetingModule optableTargetingModule(ConfigResolver configResolver,
                                                  AnalyticTagsResolver analyticTagsResolver,
                                                  OptableTargeting optableTargeting,
                                                  PayloadResolver payloadResolver,
                                                  OptableAttributesResolver optableAttributesResolver) {

        return new OptableTargetingModule(List.of(
                new OptableTargetingProcessedAuctionRequestHook(configResolver, optableTargeting, payloadResolver,
                        optableAttributesResolver),
                new OptableTargetingAuctionResponseHook(analyticTagsResolver, payloadResolver, configResolver)));
    }
}
