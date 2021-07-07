package org.prebid.server.spring.config;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import org.mapstruct.factory.Mappers;
import org.prebid.server.execution.RemoteFileSyncer;
import org.prebid.server.geolocation.CircuitBreakerSecuredGeoLocationService;
import org.prebid.server.geolocation.GeoLocationService;
import org.prebid.server.geolocation.MaxMindGeoLocationService;
import org.prebid.server.geolocation.MedianetGeoService;
import org.prebid.server.geolocation.model.medianet.GeoInfoMapper;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.metric.Metrics;
import org.prebid.server.spring.config.model.CircuitBreakerProperties;
import org.prebid.server.spring.config.model.HttpClientProperties;
import org.prebid.server.spring.config.model.MedianetGeoServiceProperties;
import org.prebid.server.spring.config.model.RemoteFileSyncerProperties;
import org.prebid.server.vertx.http.HttpClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

public class GeoLocationConfiguration {

    @Configuration
    @ConditionalOnExpression("${geolocation.enabled} == true and '${geolocation.type}' == 'maxmind'")
    static class MaxMindGeoLocationConfiguration {

        @Bean
        @ConditionalOnProperty(prefix = "geolocation.circuit-breaker", name = "enabled", havingValue = "true")
        @ConfigurationProperties(prefix = "geolocation.circuit-breaker")
        CircuitBreakerProperties maxMindCircuitBreakerProperties() {
            return new CircuitBreakerProperties();
        }

        @Bean
        @ConfigurationProperties(prefix = "geolocation.maxmind.remote-file-syncer")
        RemoteFileSyncerProperties maxMindRemoteFileSyncerProperties() {
            return new RemoteFileSyncerProperties();
        }

        @Bean
        @ConditionalOnProperty(prefix = "geolocation.circuit-breaker", name = "enabled", havingValue = "false",
                matchIfMissing = true)
        GeoLocationService basicGeoLocationService(RemoteFileSyncerProperties fileSyncerProperties,
                                                   Vertx vertx) {

            return createGeoLocationService(fileSyncerProperties, vertx);
        }

        @Bean
        @ConditionalOnProperty(prefix = "geolocation.circuit-breaker", name = "enabled", havingValue = "true")
        CircuitBreakerSecuredGeoLocationService circuitBreakerSecuredGeoLocationService(
                Vertx vertx,
                Metrics metrics,
                RemoteFileSyncerProperties fileSyncerProperties,
                @Qualifier("maxMindCircuitBreakerProperties") CircuitBreakerProperties circuitBreakerProperties,
                Clock clock) {

            return new CircuitBreakerSecuredGeoLocationService(vertx,
                    createGeoLocationService(fileSyncerProperties, vertx), metrics,
                    circuitBreakerProperties.getOpeningThreshold(), circuitBreakerProperties.getOpeningIntervalMs(),
                    circuitBreakerProperties.getClosingIntervalMs(), clock);
        }

        private GeoLocationService createGeoLocationService(RemoteFileSyncerProperties fileSyncerProperties,
                                                            Vertx vertx) {

            final HttpClientProperties httpClientProperties = fileSyncerProperties.getHttpClient();
            final HttpClientOptions httpClientOptions = new HttpClientOptions()
                    .setConnectTimeout(httpClientProperties.getConnectTimeoutMs())
                    .setMaxRedirects(httpClientProperties.getMaxRedirects());

            final RemoteFileSyncer remoteFileSyncer = RemoteFileSyncer.create(fileSyncerProperties.getDownloadUrl(),
                    fileSyncerProperties.getSaveFilepath(), fileSyncerProperties.getTmpFilepath(),
                    fileSyncerProperties.getRetryCount(), fileSyncerProperties.getRetryIntervalMs(),
                    fileSyncerProperties.getTimeoutMs(), fileSyncerProperties.getUpdateIntervalMs(),
                    vertx.createHttpClient(httpClientOptions), vertx, vertx.fileSystem());
            final MaxMindGeoLocationService maxMindGeoLocationService = new MaxMindGeoLocationService();

            remoteFileSyncer.syncForFilepath(maxMindGeoLocationService);
            return maxMindGeoLocationService;
        }
    }

    @Configuration
    @ConditionalOnExpression("${geolocation.enabled} == true and '${geolocation.type}' == 'medianet'")
    static class MediaNetGeoLocationConfiguration {

        @Bean
        @ConditionalOnProperty(prefix = "geolocation.circuit-breaker", name = "enabled", havingValue = "true")
        @ConfigurationProperties(prefix = "geolocation.circuit-breaker")
        CircuitBreakerProperties mediaNetCircuitBreakerProperties() {
            return new CircuitBreakerProperties();
        }

        @Bean
        @ConfigurationProperties(prefix = "geolocation.medianet")
        MedianetGeoServiceProperties medianetGeoServiceProperties() {
            return new MedianetGeoServiceProperties();
        }

        @Bean
        @ConditionalOnProperty(
                prefix = "geolocation.circuit-breaker",
                name = "enabled",
                havingValue = "false",
                matchIfMissing = true)
        GeoLocationService basicGeoLocationService(
                HttpClient httpClient,
                JacksonMapper jacksonMapper,
                MedianetGeoServiceProperties mediaNetGeoServiceProperties) {
            return createGeoLocationService(httpClient, jacksonMapper, mediaNetGeoServiceProperties);
        }

        @Bean
        @ConditionalOnProperty(prefix = "geolocation.circuit-breaker", name = "enabled", havingValue = "true")
        CircuitBreakerSecuredGeoLocationService circuitBreakerSecuredGeoLocationService(
                HttpClient httpClient,
                JacksonMapper jacksonMapper,
                MedianetGeoServiceProperties mediaNetGeoServiceProperties,
                Vertx vertx,
                Metrics metrics,
                @Qualifier("mediaNetCircuitBreakerProperties") CircuitBreakerProperties circuitBreakerProperties,
                Clock clock) {

            return new CircuitBreakerSecuredGeoLocationService(
                vertx,
                createGeoLocationService(httpClient, jacksonMapper, mediaNetGeoServiceProperties),
                metrics,
                circuitBreakerProperties.getOpeningThreshold(),
                circuitBreakerProperties.getOpeningIntervalMs(),
                circuitBreakerProperties.getClosingIntervalMs(),
                clock);
        }

        private GeoLocationService createGeoLocationService(
                HttpClient httpClient,
                JacksonMapper jacksonMapper,
                MedianetGeoServiceProperties mediaNetGeoServiceProperties) {
            GeoInfoMapper geoInfoMapper = Mappers.getMapper(GeoInfoMapper.class);
            String endpoint = mediaNetGeoServiceProperties.getEndpoint();
            long timeout = mediaNetGeoServiceProperties.getTimeout();
            return new MedianetGeoService(httpClient, jacksonMapper, geoInfoMapper, endpoint, timeout);
        }
    }

}
