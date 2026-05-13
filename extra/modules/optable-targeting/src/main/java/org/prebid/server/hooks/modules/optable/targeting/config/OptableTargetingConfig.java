package org.prebid.server.hooks.modules.optable.targeting.config;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.privacy.enforcement.mask.UserFpdActivityMask;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.cache.PbcStorageService;
import org.prebid.server.hooks.execution.model.ExecutionPlan;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.hooks.modules.optable.targeting.v1.OptableBidderRequestHook;
import org.prebid.server.hooks.modules.optable.targeting.v1.OptableRawAuctionRequestHook;
import org.prebid.server.hooks.modules.optable.targeting.v1.OptableTargetingAuctionResponseHook;
import org.prebid.server.hooks.modules.optable.targeting.v1.OptableTargetingModule;
import org.prebid.server.hooks.modules.optable.targeting.v1.OptableTargetingProcessedAuctionRequestHook;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.AliasesResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.BidderEnrichmentSampler;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.Cache;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.ConfigResolver;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.IdsMapper;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.NetworkCall;
import org.prebid.server.hooks.modules.optable.targeting.v1.core.OptableTargeting;
import org.prebid.server.hooks.modules.optable.targeting.v1.net.APIClientImpl;
import org.prebid.server.hooks.modules.optable.targeting.v1.net.CachedAPIClient;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.json.ObjectMapperProvider;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@ConditionalOnProperty(prefix = "hooks." + OptableTargetingModule.CODE, name = "enabled", havingValue = "true")
@Configuration
public class OptableTargetingConfig {

    @Bean
    @ConfigurationProperties(prefix = "hooks.modules." + OptableTargetingModule.CODE)
    OptableTargetingProperties optableTargetingProperties() {
        return new OptableTargetingProperties();
    }

    @Bean
    IdsMapper queryParametersExtractor(@Value("${logging.sampling-rate:0.01}") double logSamplingRate) {
        return new IdsMapper(ObjectMapperProvider.mapper(), logSamplingRate);
    }

    @Bean
    APIClientImpl apiClient(HttpClient httpClient,
                            @Value("${logging.sampling-rate:0.01}")
                            double logSamplingRate,
                            OptableTargetingProperties optableTargetingProperties,
                            JacksonMapper jacksonMapperr) {

        return new APIClientImpl(
                optableTargetingProperties.getApiEndpoint(),
                httpClient,
                jacksonMapperr,
                logSamplingRate);
    }

    @Bean
    @ConditionalOnProperty(name = {"storage.pbc.enabled", "cache.module.enabled"}, havingValue = "true")
    CachedAPIClient cachedApiClient(APIClientImpl apiClient,
                                    Cache cache,
                                    @Value("${http-client.circuit-breaker.enabled:false}")
                                    boolean isCircuitBreakerEnabled) {

        return new CachedAPIClient(apiClient, cache, isCircuitBreakerEnabled);
    }

    @Bean
    @ConditionalOnProperty(name = {"storage.pbc.enabled", "cache.module.enabled"}, havingValue = "true")
    Cache cache(PbcStorageService cacheService, JacksonMapper jacksonMapper) {
        return new Cache(cacheService, jacksonMapper);
    }

    @Bean
    OptableTargeting optableTargeting(IdsMapper parametersExtractor,
                                      APIClientImpl apiClient,
                                      @Autowired(required = false) CachedAPIClient cachedApiClient) {

        return new OptableTargeting(
                parametersExtractor,
                ObjectUtils.firstNonNull(cachedApiClient, apiClient));
    }

    @Bean
    ConfigResolver configResolver(JsonMerger jsonMerger, OptableTargetingProperties globalProperties) {
        return new ConfigResolver(ObjectMapperProvider.mapper(), jsonMerger, globalProperties);
    }

    @Bean
    NetworkCall networkCall(OptableTargeting optableTargeting, UserFpdActivityMask userFpdActivityMask) {
        return new NetworkCall(optableTargeting, userFpdActivityMask);
    }

    @Bean
    OptableTargetingModule optableTargetingModule(ConfigResolver configResolver,
                                                  NetworkCall networkCall,
                                                  JsonMerger jsonMerger,
                                                  BidderCatalog bidderCatalog,
                                                  JacksonMapper mapper,
                                                  @Value("${hooks.host-execution-plan}")
                                                  String executionPlan,
                                                  @Value("${logging.sampling-rate:0.01}") double logSamplingRate) {

        return new OptableTargetingModule(List.of(
                new OptableRawAuctionRequestHook(
                        configResolver,
                        networkCall,
                        BidderEnrichmentSampler.of(AliasesResolver.of(bidderCatalog)),
                        logSamplingRate),
                new OptableTargetingProcessedAuctionRequestHook(
                        configResolver,
                        networkCall,
                        StringUtils.isNoneEmpty(executionPlan)
                                ? mapper.decodeValue(executionPlan, ExecutionPlan.class)
                                : null,
                        logSamplingRate),
                new OptableBidderRequestHook(),
                new OptableTargetingAuctionResponseHook(
                        configResolver,
                        ObjectMapperProvider.mapper(),
                        jsonMerger)));
    }
}
