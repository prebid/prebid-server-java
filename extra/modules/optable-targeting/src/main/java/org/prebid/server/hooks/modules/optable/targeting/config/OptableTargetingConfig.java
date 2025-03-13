package org.prebid.server.hooks.modules.optable.targeting.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Vertx;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.AuctionResponseValidator;
import org.prebid.server.hooks.modules.optable.targeting.v1.net.OptableHttpClientWrapper;
import org.prebid.server.hooks.modules.optable.targeting.v1.OptableTargetingAuctionResponseHook;
import org.prebid.server.hooks.modules.optable.targeting.v1.OptableTargetingModule;
import org.prebid.server.hooks.modules.optable.targeting.v1.OptableTargetingProcessedAuctionRequestHook;
import org.prebid.server.hooks.modules.optable.targeting.v1.analytics.AnalyticTagsResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.IdsMapper;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.IpResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.OptableTargeting;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.PayloadResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.OptableAttributesResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.QueryBuilder;
import org.prebid.server.hooks.modules.optable.targeting.v1.net.APIClient;
import org.prebid.server.hooks.modules.optable.targeting.v1.net.OptableResponseParser;
import org.prebid.server.json.JacksonMapper;
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
        return new ObjectMapper();
    }

    @Bean
    AnalyticTagsResolver analyticTagsResolver(ObjectMapper objectMapper) {
        return new AnalyticTagsResolver(objectMapper);
    }

    @Bean
    IdsMapper queryParametersExtractor(OptableTargetingProperties properties, ObjectMapper objectMapper) {
        return new IdsMapper(objectMapper, properties.getPpidMapping());
    }

    @Bean
    PayloadResolver payloadResolver(ObjectMapper mapper) {
        return new PayloadResolver(mapper);
    }

    @Bean
    QueryBuilder queryBuilder(OptableTargetingProperties properties) {
        return new QueryBuilder(properties.getIdPrefixOrder());
    }

    @Bean
    OptableResponseParser optableResponseParser(JacksonMapper mapper) {
        return new OptableResponseParser(mapper);
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
                        OptableResponseParser responseParser) {

        final String endpoint = HttpUtil.validateUrl(Objects.requireNonNull(properties.getApiEndpoint()));

        return new APIClient(endpoint, httpClientWrapper.getHttpClient(), logSamplingRate, responseParser,
                properties.getApiKey());
    }

    @Bean
    OptableAttributesResolver optableAttributesResolver(IpResolver ipResolver) {
        return new OptableAttributesResolver(ipResolver);
    }

    @Bean()
    OptableTargeting optableTargeting(IdsMapper parametersExtractor,
                                      QueryBuilder queryBuilder, APIClient apiClient) {

        return new OptableTargeting(parametersExtractor, queryBuilder, apiClient);
    }

    @Bean
    IpResolver ipResolver() {
        return new IpResolver();
    }

    @Bean
    AuctionResponseValidator auctionResponseValidator() {
        return new AuctionResponseValidator();
    }

    @Bean
    OptableTargetingModule optableTargetingModule(OptableTargetingProperties properties,
                                                  AnalyticTagsResolver analyticTagsResolver,
                                                  OptableTargeting optableTargeting,
                                                  PayloadResolver payloadResolver,
                                                  OptableAttributesResolver optableAttributesResolver,
                                                  AuctionResponseValidator auctionResponseValidator) {

        return new OptableTargetingModule(List.of(
                new OptableTargetingProcessedAuctionRequestHook(properties, optableTargeting, payloadResolver,
                        optableAttributesResolver),
                new OptableTargetingAuctionResponseHook(analyticTagsResolver, payloadResolver,
                        properties.getAdserverTargeting(), auctionResponseValidator)));
    }
}
