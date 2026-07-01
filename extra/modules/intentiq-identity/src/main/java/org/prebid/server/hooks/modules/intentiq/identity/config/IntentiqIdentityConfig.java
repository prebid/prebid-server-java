package org.prebid.server.hooks.modules.intentiq.identity.config;

import com.codahale.metrics.MetricRegistry;
import io.vertx.core.Vertx;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisOptions;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.modules.intentiq.identity.cache.CacheTtlPolicy;
import org.prebid.server.hooks.modules.intentiq.identity.cache.IdentityCache;
import org.prebid.server.hooks.modules.intentiq.identity.cache.IdentityStore;
import org.prebid.server.hooks.modules.intentiq.identity.cache.RedisIdentityStore;
import org.prebid.server.hooks.modules.intentiq.identity.cache.RedisStatsReporter;
import org.prebid.server.hooks.modules.intentiq.identity.metric.IntentiqIdentityMetrics;
import org.prebid.server.hooks.modules.intentiq.identity.metric.NoopIntentiqIdentityMetrics;
import org.prebid.server.hooks.modules.intentiq.identity.model.config.CacheProperties;
import org.prebid.server.hooks.modules.intentiq.identity.model.config.IntentiqIdentityProperties;
import org.prebid.server.hooks.modules.intentiq.identity.model.config.RedisProperties;
import org.prebid.server.hooks.modules.intentiq.identity.v1.IntentiqIdentityAuctionResponseHook;
import org.prebid.server.hooks.modules.intentiq.identity.v1.IntentiqIdentityModule;
import org.prebid.server.hooks.modules.intentiq.identity.v1.IntentiqIdentityProcessedAuctionRequestHook;
import org.prebid.server.hooks.modules.intentiq.identity.v1.core.ConfigResolver;
import org.prebid.server.hooks.modules.intentiq.identity.v1.core.FirstPartyKeyExtractor;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.json.ObjectMapperProvider;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@ConditionalOnProperty(prefix = "hooks." + IntentiqIdentityModule.CODE, name = "enabled", havingValue = "true")
@Configuration
public class IntentiqIdentityConfig {

    // How often the L2 (Redis) size/eviction gauges are refreshed from DBSIZE / INFO.
    private static final long REDIS_STATS_POLL_MS = 30_000L;

    @Bean
    @ConfigurationProperties(prefix = "hooks.modules." + IntentiqIdentityModule.CODE)
    IntentiqIdentityProperties intentiqIdentityProperties() {
        return new IntentiqIdentityProperties();
    }

    @Bean
    ConfigResolver intentiqIdentityConfigResolver(JsonMerger jsonMerger, IntentiqIdentityProperties properties) {
        return new ConfigResolver(ObjectMapperProvider.mapper(), jsonMerger, properties);
    }

    /**
     * Default L2 store; can override caching by supplying your own {@link IdentityStore} bean.
     */
    @Bean
    @ConditionalOnMissingBean(IdentityStore.class)
    @ConditionalOnProperty(prefix = "hooks.modules." + IntentiqIdentityModule.CODE + ".cache",
            name = "enabled", havingValue = "true")
    IdentityStore intentiqIdentityStore(IntentiqIdentityProperties properties, Vertx vertx) {
        return new RedisIdentityStore(createRedisApi(properties.getRedis(), vertx));
    }

    @Bean
    @ConditionalOnProperty(prefix = "hooks.modules." + IntentiqIdentityModule.CODE + ".cache",
            name = "enabled", havingValue = "true")
    IdentityCache intentiqIdentityCache(IntentiqIdentityProperties properties,
                                        IdentityStore identityStore,
                                        JacksonMapper mapper,
                                        IntentiqIdentityMetrics metrics) {
        final CacheProperties cache = properties.getCache();
        final CacheTtlPolicy ttlPolicy = new CacheTtlPolicy(
                cache.getTtlseconds() * 1000L,
                cache.getTtlCeilingFirstPartySeconds() * 1000L,
                cache.getTtlCeilingThirdPartySeconds() * 1000L,
                cache.getTtlCeilingDeviceSeconds() * 1000L,
                cache.getNegativeTtlSeconds() * 1000L,
                cache.getInProgressTtlSeconds() * 1000L);

        return new IdentityCache(properties.getCacheMaxSize(), ttlPolicy, identityStore, mapper, metrics);
    }

    /**
     * Polls Redis (L2) DBSIZE / evicted_keys into the {@code l2.size} / {@code l2.eviction} gauges.
     * Only when the L2 store is the default Redis backend (a custom {@link IdentityStore} has no such
     * stats). Returns {@code null} otherwise — Spring simply does not register the bean.
     */
    @Bean
    @ConditionalOnProperty(prefix = "hooks.modules." + IntentiqIdentityModule.CODE + ".cache",
            name = "enabled", havingValue = "true")
    RedisStatsReporter intentiqIdentityRedisStatsReporter(IdentityStore identityStore,
                                                          Vertx vertx,
                                                          IntentiqIdentityMetrics metrics) {
        return identityStore instanceof RedisIdentityStore redisStore
                ? new RedisStatsReporter(redisStore, vertx, metrics, REDIS_STATS_POLL_MS).start()
                : null;
    }

    @Bean
    @ConditionalOnProperty(prefix = "hooks.modules." + IntentiqIdentityModule.CODE,
            name = "metrics-enabled", havingValue = "true", matchIfMissing = true)
    IntentiqIdentityMetrics intentiqIdentityMetrics(MetricRegistry metricRegistry) {
        return new IntentiqIdentityMetrics(metricRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(IntentiqIdentityMetrics.class)
    IntentiqIdentityMetrics noopIntentiqIdentityMetrics() {
        return new NoopIntentiqIdentityMetrics();
    }

    @Bean
    IntentiqIdentityModule intentiqIdentityModule(ConfigResolver configResolver,
                                                  HttpClient httpClient,
                                                  JacksonMapper mapper,
                                                  IntentiqIdentityMetrics metrics,
                                                  IntentiqIdentityProperties properties,
                                                  @Autowired(required = false) IdentityCache identityCache) {
        final FirstPartyKeyExtractor keyExtractor =
                new FirstPartyKeyExtractor(properties.getCache().getMaxKeys());

        return new IntentiqIdentityModule(List.of(
                new IntentiqIdentityProcessedAuctionRequestHook(
                        configResolver, httpClient, mapper, identityCache, keyExtractor, metrics),
                new IntentiqIdentityAuctionResponseHook(configResolver, httpClient, mapper, metrics)));
    }

    private static RedisAPI createRedisApi(RedisProperties redis, Vertx vertx) {
        if (redis == null || StringUtils.isBlank(redis.getHost())) {
            throw new IllegalArgumentException("hooks.modules." + IntentiqIdentityModule.CODE
                    + ".redis.host is required when cache is enabled");
        }
        if (redis.getPort() == null) {
            throw new IllegalArgumentException("hooks.modules." + IntentiqIdentityModule.CODE
                    + ".redis.port is required when cache is enabled");
        }

        final String credentials = StringUtils.isNotBlank(redis.getPassword()) ? ":" + redis.getPassword() + "@" : "";
        final RedisOptions options = new RedisOptions()
                .setConnectionString("redis://" + credentials + redis.getHost() + ":" + redis.getPort());

        return RedisAPI.api(Redis.createClient(vertx, options));
    }
}
