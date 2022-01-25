package org.prebid.server.spring.config;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import lombok.experimental.UtilityClass;
import org.prebid.server.execution.RemoteFileSyncer;
import org.prebid.server.geolocation.CircuitBreakerSecuredGeoLocationService;
import org.prebid.server.geolocation.CountryCodeMapper;
import org.prebid.server.geolocation.GeoLocationService;
import org.prebid.server.geolocation.MaxMindGeoLocationService;
import org.prebid.server.metric.Metrics;
import org.prebid.server.spring.config.model.CircuitBreakerProperties;
import org.prebid.server.spring.config.model.HttpClientProperties;
import org.prebid.server.spring.config.model.RemoteFileSyncerProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.util.FileCopyUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;

@UtilityClass
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
    static class CountryCodeMapperConfiguration {

        @Bean
        public CountryCodeMapper countryCodeMapper(
                @Value("classpath:country-codes.csv") Resource resource) throws IOException {

            final Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
            final String countryCodesCsvAsString = FileCopyUtils.copyToString(reader);
            reader.close();

            return new CountryCodeMapper(countryCodesCsvAsString);
        }
    }
}
