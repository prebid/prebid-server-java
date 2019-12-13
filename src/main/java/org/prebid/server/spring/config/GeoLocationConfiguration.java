package org.prebid.server.spring.config;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import org.prebid.server.execution.RemoteFileSyncer;
import org.prebid.server.geolocation.CircuitBreakerSecuredGeoLocationService;
import org.prebid.server.geolocation.GeoLocationService;
import org.prebid.server.geolocation.MaxMindGeoLocationService;
import org.prebid.server.metric.Metrics;
import org.prebid.server.spring.config.model.CircuitBreakerProperties;
import org.prebid.server.spring.config.model.HttpClientProperties;
import org.prebid.server.spring.config.model.RemoteFileSyncerProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
@ConditionalOnProperty(prefix = "geolocation", name = "enabled", havingValue = "true")
public class GeoLocationConfiguration {

    @Bean
    @ConfigurationProperties(prefix = "geolocation.circuit-breaker")
    @ConditionalOnProperty(prefix = "geolocation.circuit-breaker", name = "enabled", havingValue = "true")
    CircuitBreakerProperties geolocationCircuitBreakerProperties() {
        return new CircuitBreakerProperties();
    }

    @Configuration
    @ConditionalOnProperty(prefix = "geolocation", name = "type", havingValue = "maxmind")
    static class MaxMindGeoLocationConfiguration {

        @Bean
        @ConfigurationProperties(prefix = "geolocation.maxmind.remote-file-syncer")
        RemoteFileSyncerProperties maxMindRemoteFileSyncerProperties() {
            return new RemoteFileSyncerProperties();
        }

        /**
         * Default geolocation service implementation.
         */
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
                @Qualifier("geolocationCircuitBreakerProperties") CircuitBreakerProperties circuitBreakerProperties,
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
                    fileSyncerProperties.getSaveFilepath(), fileSyncerProperties.getRetryCount(),
                    fileSyncerProperties.getRetryIntervalMs(), fileSyncerProperties.getTimeoutMs(),
                    vertx.createHttpClient(httpClientOptions), vertx);
            final MaxMindGeoLocationService maxMindGeoLocationService = new MaxMindGeoLocationService();

            remoteFileSyncer.syncForFilepath(maxMindGeoLocationService);
            return maxMindGeoLocationService;
        }
    }
}
