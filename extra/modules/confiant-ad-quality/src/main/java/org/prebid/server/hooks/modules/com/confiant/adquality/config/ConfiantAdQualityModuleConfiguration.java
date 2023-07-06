package org.prebid.server.hooks.modules.com.confiant.adquality.config;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.prebid.server.hooks.modules.com.confiant.adquality.core.RedisClient;
import org.prebid.server.hooks.modules.com.confiant.adquality.core.RedisScanStateChecker;
import org.prebid.server.hooks.modules.com.confiant.adquality.core.RedisVerticle;
import org.prebid.server.hooks.modules.com.confiant.adquality.v1.ConfiantAdQualityBidResponsesScanHook;
import org.prebid.server.hooks.modules.com.confiant.adquality.v1.ConfiantAdQualityModule;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
            @Value("${hooks.modules.confiant-ad-quality.redis-host}") String host,
            @Value("${hooks.modules.confiant-ad-quality.redis-port}") int port,
            @Value("${hooks.modules.confiant-ad-quality.redis-password}") String password,
            @Value("${hooks.modules.confiant-ad-quality.scan-state-check-delay}") int checkDelay,
            Vertx vertx) {
        final RedisVerticle redisVerticle = new RedisVerticle(vertx, host, port, password);
        final RedisClient redisClient = new RedisClient(redisVerticle, apiKey);
        final RedisScanStateChecker redisScanStateChecker = new RedisScanStateChecker(redisClient, checkDelay, vertx);

        // Initialize Redis connection and scan state check timer
        final Promise<Void> startClient = Promise.promise();
        startClient.future().onComplete(r -> redisScanStateChecker.run());

        redisClient.start(startClient);

        return new ConfiantAdQualityModule(List.of(
                new ConfiantAdQualityBidResponsesScanHook(redisClient, redisScanStateChecker)));
    }
}
