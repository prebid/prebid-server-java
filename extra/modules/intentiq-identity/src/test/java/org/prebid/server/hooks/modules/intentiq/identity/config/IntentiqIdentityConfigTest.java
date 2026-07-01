package org.prebid.server.hooks.modules.intentiq.identity.config;

import com.codahale.metrics.MetricRegistry;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.hooks.modules.intentiq.identity.cache.IdentityCache;
import org.prebid.server.hooks.modules.intentiq.identity.cache.IdentityStore;
import org.prebid.server.hooks.modules.intentiq.identity.cache.RedisIdentityStore;
import org.prebid.server.hooks.modules.intentiq.identity.cache.RedisStatsReporter;
import org.prebid.server.hooks.modules.intentiq.identity.metric.IntentiqIdentityMetrics;
import org.prebid.server.hooks.modules.intentiq.identity.metric.NoopIntentiqIdentityMetrics;
import org.prebid.server.hooks.modules.intentiq.identity.model.config.IntentiqIdentityProperties;
import org.prebid.server.hooks.modules.intentiq.identity.model.config.RedisProperties;
import org.prebid.server.hooks.modules.intentiq.identity.v1.IntentiqIdentityModule;
import org.prebid.server.hooks.modules.intentiq.identity.v1.core.ConfigResolver;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.json.ObjectMapperProvider;
import org.prebid.server.vertx.httpclient.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class IntentiqIdentityConfigTest {

    private static final JacksonMapper MAPPER = new JacksonMapper(ObjectMapperProvider.mapper());

    @Mock
    private HttpClient httpClient;

    @Mock
    private Vertx vertx;

    private final IntentiqIdentityConfig target = new IntentiqIdentityConfig();

    @Test
    public void propertiesBeanShouldBeCreated() {
        assertThat(target.intentiqIdentityProperties()).isInstanceOf(IntentiqIdentityProperties.class);
    }

    @Test
    public void configResolverBeanShouldBeCreated() {
        assertThat(target.intentiqIdentityConfigResolver(new JsonMerger(MAPPER), new IntentiqIdentityProperties()))
                .isInstanceOf(ConfigResolver.class);
    }

    @Test
    public void moduleBeanShouldExposeProcessedAuctionRequestHookUnderModuleCode() {
        // given
        final ConfigResolver configResolver =
                new ConfigResolver(MAPPER.mapper(), new JsonMerger(MAPPER), new IntentiqIdentityProperties());

        // when
        final IntentiqIdentityModule module = target.intentiqIdentityModule(
                configResolver,
                httpClient,
                MAPPER,
                new IntentiqIdentityMetrics(new MetricRegistry()),
                new IntentiqIdentityProperties(),
                mock(IdentityCache.class));

        // then
        assertThat(module.code()).isEqualTo(IntentiqIdentityModule.CODE);
        assertThat(module.hooks()).hasSize(2);
    }

    @Test
    public void metricsBeanShouldBeCreated() {
        assertThat(target.intentiqIdentityMetrics(new MetricRegistry()))
                .isInstanceOf(IntentiqIdentityMetrics.class);
    }

    @Test
    public void noopMetricsBeanShouldBeCreated() {
        assertThat(target.noopIntentiqIdentityMetrics())
                .isInstanceOf(NoopIntentiqIdentityMetrics.class);
    }

    @Test
    public void identityStoreBeanShouldFailWhenRedisHostMissing() {
        // given
        final IntentiqIdentityProperties properties = new IntentiqIdentityProperties();
        properties.setRedis(new RedisProperties());

        // when and then
        assertThatThrownBy(() -> target.intentiqIdentityStore(properties, vertx))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void identityStoreBeanShouldFailWhenRedisPortMissing() {
        // given
        final RedisProperties redis = new RedisProperties();
        redis.setHost("localhost");
        final IntentiqIdentityProperties properties = new IntentiqIdentityProperties();
        properties.setRedis(redis);

        // when and then
        assertThatThrownBy(() -> target.intentiqIdentityStore(properties, vertx))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void redisStatsReporterBeanShouldReturnReporterWhenStoreIsRedis() {
        // given
        final RedisIdentityStore store = mock(RedisIdentityStore.class);
        when(store.dbSize()).thenReturn(Future.succeededFuture(0L));
        when(store.evictedKeys()).thenReturn(Future.succeededFuture(0L));

        // when
        final RedisStatsReporter reporter = target.intentiqIdentityRedisStatsReporter(
                store, vertx, new IntentiqIdentityMetrics(new MetricRegistry()));

        // then
        assertThat(reporter).isNotNull();
    }

    @Test
    public void redisStatsReporterBeanShouldReturnNullWhenStoreIsNotRedis() {
        // when and then — a custom (non-Redis) IdentityStore has no DBSIZE/INFO stats to poll
        assertThat(target.intentiqIdentityRedisStatsReporter(
                mock(IdentityStore.class), vertx, new IntentiqIdentityMetrics(new MetricRegistry())))
                .isNull();
    }

    @Test
    public void identityStoreBeanShouldBeBuiltWhenRedisPasswordConfigured() {
        // given — a real Vertx is required because the Redis client casts it to VertxInternal
        final Vertx realVertx = Vertx.vertx();
        final RedisProperties redis = new RedisProperties();
        redis.setHost("redis.internal");
        redis.setPort(6390);
        redis.setPassword("s3cret");
        final IntentiqIdentityProperties properties = new IntentiqIdentityProperties();
        properties.setRedis(redis);

        try {
            // when and then
            assertThat(target.intentiqIdentityStore(properties, realVertx)).isNotNull();
        } finally {
            realVertx.close();
        }
    }

    @Test
    public void identityStoreAndCacheBeansShouldBeBuiltWhenRedisConfigured() {
        // given — a real Vertx is required because the Redis client casts it to VertxInternal
        final Vertx realVertx = Vertx.vertx();
        final RedisProperties redis = new RedisProperties();
        redis.setHost("redis.internal");
        redis.setPort(6390);
        final IntentiqIdentityProperties properties = new IntentiqIdentityProperties();
        properties.setRedis(redis);

        try {
            // when
            final IdentityStore store = target.intentiqIdentityStore(properties, realVertx);
            final IdentityCache cache = target.intentiqIdentityCache(
                    properties, store, MAPPER, new IntentiqIdentityMetrics(new MetricRegistry()));

            // then
            assertThat(store).isNotNull();
            assertThat(cache).isNotNull();
        } finally {
            realVertx.close();
        }
    }
}
