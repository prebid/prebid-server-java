package org.prebid.server.hooks.modules.intentiq.identity.config;

import com.aerospike.client.AerospikeClient;
import com.aerospike.client.Host;
import com.aerospike.client.async.EventLoops;
import com.aerospike.client.async.EventPolicy;
import com.aerospike.client.async.NioEventLoops;
import com.aerospike.client.policy.ClientPolicy;
import com.codahale.metrics.MetricRegistry;
import io.vertx.core.Vertx;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.PemTrustOptions;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisClientType;
import io.vertx.redis.client.RedisOptions;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.modules.intentiq.identity.cache.AerospikeIdentityStore;
import org.prebid.server.hooks.modules.intentiq.identity.cache.CacheTtlPolicy;
import org.prebid.server.hooks.modules.intentiq.identity.cache.IdentityCache;
import org.prebid.server.hooks.modules.intentiq.identity.cache.IdentityStore;
import org.prebid.server.hooks.modules.intentiq.identity.cache.RedisIdentityStore;
import org.prebid.server.hooks.modules.intentiq.identity.cache.RedisStatsReporter;
import org.prebid.server.hooks.modules.intentiq.identity.metric.IntentiqIdentityMetrics;
import org.prebid.server.hooks.modules.intentiq.identity.metric.MetricsPusher;
import org.prebid.server.hooks.modules.intentiq.identity.metric.NoopIntentiqIdentityMetrics;
import org.prebid.server.hooks.modules.intentiq.identity.model.config.AerospikeProperties;
import org.prebid.server.hooks.modules.intentiq.identity.model.config.CacheProperties;
import org.prebid.server.hooks.modules.intentiq.identity.model.config.IntentiqIdentityProperties;
import org.prebid.server.hooks.modules.intentiq.identity.model.config.MetricsPushProperties;
import org.prebid.server.hooks.modules.intentiq.identity.model.config.RedisProperties;
import org.prebid.server.hooks.modules.intentiq.identity.v1.IntentiqIdentityAuctionResponseHook;
import org.prebid.server.hooks.modules.intentiq.identity.v1.IntentiqIdentityModule;
import org.prebid.server.hooks.modules.intentiq.identity.v1.IntentiqIdentityProcessedAuctionRequestHook;
import org.prebid.server.hooks.modules.intentiq.identity.v1.IntentiqIdentityRawAuctionRequestHook;
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

    private static final String HOSTNAME_VERIFICATION_HTTPS = "HTTPS";

    private static final String PROVIDER_REDIS = "redis";
    private static final String PROVIDER_AEROSPIKE = "aerospike";

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
        final String provider = properties.getCache().getProvider();
        if (StringUtils.isBlank(provider) || PROVIDER_REDIS.equalsIgnoreCase(provider)) {
            return new RedisIdentityStore(createRedisApi(properties.getRedis(), vertx));
        }
        if (PROVIDER_AEROSPIKE.equalsIgnoreCase(provider)) {
            return createAerospikeStore(properties.getAerospike());
        }
        throw new IllegalArgumentException("hooks.modules." + IntentiqIdentityModule.CODE
                + ".cache.provider must be redis or aerospike, not: " + provider);
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

        return new IdentityCache(
                properties.getCacheMaxSize(), ttlPolicy, identityStore, mapper, metrics, cache.getL2TimeoutMs());
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

    /**
     * Pushes the module's metrics to IntentIQ when {@code metrics-push.url} and {@code metrics-push.token} are
     * set and metrics are recorded. Returns {@code null} otherwise, so Spring registers no bean.
     */
    @Bean
    @ConditionalOnProperty(prefix = "hooks.modules." + IntentiqIdentityModule.CODE,
            name = "metrics-enabled", havingValue = "true", matchIfMissing = true)
    MetricsPusher intentiqIdentityMetricsPusher(IntentiqIdentityProperties properties,
                                                MetricRegistry metricRegistry,
                                                IntentiqIdentityMetrics metrics,
                                                Vertx vertx,
                                                HttpClient httpClient) {

        final MetricsPushProperties push = properties.getMetricsPush();
        if (push == null || StringUtils.isAnyBlank(push.getUrl(), push.getToken())) {
            return null;
        }
        if (push.getIntervalSeconds() <= 0 || push.getTimeoutMs() <= 0) {
            throw new IllegalArgumentException("hooks.modules." + IntentiqIdentityModule.CODE
                    + ".metrics-push.interval-seconds and timeout-ms must be positive");
        }

        return new MetricsPusher(metricRegistry, metrics, vertx, httpClient, push.getUrl(), push.getToken(),
                MetricsPusher.resolveInstance(push.getInstance()),
                push.getIntervalSeconds() * 1000L, push.getTimeoutMs()).start();
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
                new IntentiqIdentityRawAuctionRequestHook(),
                new IntentiqIdentityProcessedAuctionRequestHook(
                        configResolver, httpClient, mapper, identityCache, keyExtractor, metrics),
                new IntentiqIdentityAuctionResponseHook(configResolver, httpClient, mapper, metrics)));
    }

    private static AerospikeIdentityStore createAerospikeStore(AerospikeProperties aerospike) {
        if (aerospike == null || StringUtils.isAnyBlank(aerospike.getHost(), aerospike.getNamespace(),
                aerospike.getSet())) {
            throw new IllegalArgumentException("hooks.modules." + IntentiqIdentityModule.CODE
                    + ".aerospike.host, namespace and set are required when cache.provider is aerospike");
        }

        final EventLoops eventLoops = new NioEventLoops(new EventPolicy(), aerospike.getEventLoopThreads());
        final ClientPolicy policy = new ClientPolicy();
        policy.eventLoops = eventLoops;
        // PBS starts even when Aerospike is down; the client keeps reconnecting and the cache fails open.
        policy.failIfNotConnected = false;
        policy.timeout = aerospike.getConnectTimeoutMs();
        policy.asyncMaxConnsPerNode = aerospike.getMaxConnectionsPerNode();
        if (StringUtils.isNotBlank(aerospike.getUser())) {
            policy.user = aerospike.getUser();
            policy.password = aerospike.getPassword();
        }

        return new AerospikeIdentityStore(
                new AerospikeClient(policy, new Host(aerospike.getHost(), aerospike.getPort())),
                eventLoops,
                aerospike.getNamespace(),
                aerospike.getSet(),
                aerospike.getTimeoutMs());
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

        // Credentials are set as options, not in the connection string, so a password with URL-reserved
        // characters (@, :, /, %) is not mangled.
        final RedisOptions options = new RedisOptions()
                .setType(redis.isCluster() ? RedisClientType.CLUSTER : RedisClientType.STANDALONE)
                .setConnectionString((redis.isSsl() ? "rediss://" : "redis://") + redis.getHost() + ":" + redis.getPort())
                .setMaxPoolSize(redis.getMaxPoolSize())
                .setMaxPoolWaiting(redis.getMaxPoolWaiting());
        if (redis.isSsl()) {
            // Vert.x refuses a TLS connection unless a hostname verification algorithm is set; an empty one
            // turns the check off.
            final NetClientOptions netClientOptions = options.getNetClientOptions()
                    .setHostnameVerificationAlgorithm(redis.isVerifyHostname() ? HOSTNAME_VERIFICATION_HTTPS : "");
            if (StringUtils.isNotBlank(redis.getCaCertPath())) {
                netClientOptions.setTrustOptions(new PemTrustOptions().addCertPath(redis.getCaCertPath()));
            }
        }
        if (StringUtils.isNotBlank(redis.getUsername())) {
            options.setUser(redis.getUsername());
        }
        if (StringUtils.isNotBlank(redis.getPassword())) {
            options.setPassword(redis.getPassword());
        }

        return RedisAPI.api(Redis.createClient(vertx, options));
    }
}
