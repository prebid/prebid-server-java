package org.prebid.server.hooks.modules.com.confiant.adquality.config;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.prebid.server.auction.PrivacyEnforcementService;
import org.prebid.server.hooks.modules.com.confiant.adquality.core.BidsScanner;
import org.prebid.server.hooks.modules.com.confiant.adquality.core.RedisScanStateChecker;
import org.prebid.server.hooks.modules.com.confiant.adquality.core.RedisClient;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.RedisConfig;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.RedisConnectionConfig;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.RedisRetryConfig;
import org.prebid.server.hooks.modules.com.confiant.adquality.v1.ConfiantAdQualityBidResponsesScanHook;
import org.prebid.server.hooks.modules.com.confiant.adquality.v1.ConfiantAdQualityModule;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import java.util.List;

@ConditionalOnProperty(prefix = "hooks." + ConfiantAdQualityModule.CODE, name = "enabled", havingValue = "true")
@PropertySource(
        value = "classpath:/module-config/confiant-ad-quality.yaml",
        factory = YamlPropertySourceFactory.class)
@Configuration
public class ConfiantAdQualityModuleConfiguration {

    @Bean
    ConfiantAdQualityModule confiantAdQualityModule(
            @Value("${hooks.modules.confiant-ad-quality.api-key}") String apiKey,
            @Value("${hooks.modules.confiant-ad-quality.scan-state-check-interval}") int scanStateCheckInterval,
            RedisConfig redisConfig,
            RedisRetryConfig retryConfig,
            Vertx vertx,
            PrivacyEnforcementService privacyEnforcementService) {
        final RedisConnectionConfig writeNodeConfig = redisConfig.getWriteNode();
        final RedisClient writeRedisNode = new RedisClient(
                vertx, writeNodeConfig.getHost(), writeNodeConfig.getPort(), writeNodeConfig.getPassword(), retryConfig, "write node");
        final RedisConnectionConfig readNodeConfig = redisConfig.getReadNode();
        final RedisClient readRedisNode = new RedisClient(
                vertx, readNodeConfig.getHost(), readNodeConfig.getPort(), readNodeConfig.getPassword(), retryConfig, "read node");

        final BidsScanner bidsScanner = new BidsScanner(writeRedisNode, readRedisNode, apiKey);
        final RedisScanStateChecker redisScanStateChecker = new RedisScanStateChecker(bidsScanner, scanStateCheckInterval, vertx);

        final Promise<Void> scannerPromise = Promise.promise();
        scannerPromise.future().onComplete(r -> redisScanStateChecker.run());

        bidsScanner.start(scannerPromise);

        return new ConfiantAdQualityModule(List.of(
                new ConfiantAdQualityBidResponsesScanHook(bidsScanner, privacyEnforcementService)));
    }

    @Bean
    @ConfigurationProperties(prefix = "hooks.modules.confiant-ad-quality.redis-config")
    RedisConfig redisConfig() {
        return new RedisConfig();
    }

    @Bean
    @ConfigurationProperties(prefix = "hooks.modules.confiant-ad-quality.redis-retry-config")
    RedisRetryConfig redisRetryConfig() {
        return new RedisRetryConfig();
    }
}
