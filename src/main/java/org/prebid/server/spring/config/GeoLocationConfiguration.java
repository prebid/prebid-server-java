package org.prebid.server.spring.config;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClientOptions;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.GeoLocationServiceWrapper;
import org.prebid.server.auction.requestfactory.Ortb2ImplicitParametersResolver;
import org.prebid.server.execution.RemoteFileSyncer;
import org.prebid.server.execution.retry.FixedIntervalRetryPolicy;
import org.prebid.server.geolocation.CircuitBreakerSecuredGeoLocationService;
import org.prebid.server.geolocation.ConfigurationGeoLocationService;
import org.prebid.server.geolocation.CountryCodeMapper;
import org.prebid.server.geolocation.GeoLocationService;
import org.prebid.server.geolocation.MaxMindGeoLocationService;
import org.prebid.server.metric.Metrics;
import org.prebid.server.spring.config.model.CircuitBreakerProperties;
import org.prebid.server.spring.config.model.HttpClientProperties;
import org.prebid.server.spring.config.model.RemoteFileSyncerProperties;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Configuration
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

        private GeoLocationService createGeoLocationService(RemoteFileSyncerProperties properties, Vertx vertx) {
            final HttpClientProperties httpClientProperties = properties.getHttpClient();
            final HttpClientOptions httpClientOptions = new HttpClientOptions()
                    .setConnectTimeout(httpClientProperties.getConnectTimeoutMs())
                    .setMaxRedirects(httpClientProperties.getMaxRedirects());

            final RemoteFileSyncer remoteFileSyncer = new RemoteFileSyncer(
                    properties.getDownloadUrl(),
                    properties.getSaveFilepath(),
                    properties.getTmpFilepath(),
                    FixedIntervalRetryPolicy.limited(properties.getRetryIntervalMs(), properties.getRetryCount()),
                    properties.getTimeoutMs(),
                    properties.getUpdateIntervalMs(),
                    vertx.createHttpClient(httpClientOptions),
                    vertx);
            final MaxMindGeoLocationService maxMindGeoLocationService = new MaxMindGeoLocationService();

            remoteFileSyncer.sync(maxMindGeoLocationService);
            return maxMindGeoLocationService;
        }
    }

    @Configuration
    @ConditionalOnExpression("${geolocation.enabled} == true and '${geolocation.type}' == 'configuration'")
    static class ConfigurationGeoLocationConfiguration {

        @Bean
        @ConfigurationProperties("geolocation.configurations")
        public List<GeoInfoConfiguration> configurations() {
            return new ArrayList<>();
        }

        @Bean
        public GeoLocationService configurationGeoLocationService(List<GeoInfoConfiguration> configs) {
            return new ConfigurationGeoLocationService(
                    configs.stream()
                            .filter(config -> config != null && config.getAddressPattern() != null)
                            .map(ConfigurationGeoLocationConfiguration::from)
                            .toList());
        }

        private static org.prebid.server.geolocation.model.GeoInfoConfiguration from(
                GeoInfoConfiguration config) {

            final GeoInfo geoInfo = config.getGeoInfo();

            return org.prebid.server.geolocation.model.GeoInfoConfiguration.of(
                    config.getAddressPattern(),
                    geoInfo != null
                            ? org.prebid.server.geolocation.model.GeoInfo.builder()
                            .vendor(StringUtils.EMPTY)
                            .continent(geoInfo.getContinent())
                            .country(geoInfo.getCountry())
                            .region(geoInfo.getRegion())
                            .city(geoInfo.getCity())
                            .metroGoogle(geoInfo.getMetroGoogle())
                            .metroNielsen(geoInfo.getMetroNielsen())
                            .zip(geoInfo.getZip())
                            .connectionSpeed(geoInfo.getConnectionSpeed())
                            .lat(geoInfo.getLat())
                            .lon(geoInfo.getLon())
                            .timeZone(geoInfo.getTimeZone())
                            .build()
                            : null);
        }

        @Data
        static class GeoInfoConfiguration {

            String addressPattern;

            GeoInfo geoInfo;
        }

        @Data
        static class GeoInfo {

            String continent;

            String country;

            String region;

            Integer regionCode;

            String city;

            String metroGoogle;

            Integer metroNielsen;

            String zip;

            String connectionSpeed;

            Float lat;

            Float lon;

            ZoneId timeZone;
        }
    }

    @Bean
    public CountryCodeMapper countryCodeMapper(@Value("classpath:country-codes.csv") Resource countryCodes,
                                               @Value("classpath:mcc-country-codes.csv") Resource mccCountryCodes)
            throws IOException {

        return new CountryCodeMapper(readCsv(countryCodes), readCsv(mccCountryCodes));
    }

    private String readCsv(Resource resource) throws IOException {
        final Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
        final String csv = FileCopyUtils.copyToString(reader);
        reader.close();
        return csv;
    }

    @Bean
    GeoLocationServiceWrapper geoLocationServiceWrapper(
            @Autowired(required = false) GeoLocationService geoLocationService,
            Ortb2ImplicitParametersResolver implicitParametersResolver,
            Metrics metrics) {

        return new GeoLocationServiceWrapper(
                geoLocationService,
                implicitParametersResolver,
                metrics);
    }

}
