package org.prebid.server.spring.config;

import io.vertx.core.Vertx;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.reporter.AnalyticsReporterDelegator;
import org.prebid.server.analytics.reporter.log.LogAnalyticsReporter;
import org.prebid.server.analytics.reporter.pubstack.PubstackAnalyticsReporter;
import org.prebid.server.analytics.reporter.pubstack.model.PubstackAnalyticsProperties;
import org.prebid.server.auction.PrivacyEnforcementService;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.metric.Metrics;
import org.prebid.server.vertx.http.HttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;

@Configuration
public class AnalyticsConfiguration {

    @Bean
    AnalyticsReporterDelegator analyticsReporterDelegator(
            @Autowired(required = false) List<AnalyticsReporter> delegates,
            Vertx vertx,
            PrivacyEnforcementService privacyEnforcementService,
            Metrics metrics) {

        return new AnalyticsReporterDelegator(
                delegates != null ? delegates : Collections.emptyList(),
                vertx,
                privacyEnforcementService,
                metrics);
    }

    @Bean
    @ConditionalOnProperty(prefix = "analytics.log", name = "enabled", havingValue = "true")
    LogAnalyticsReporter logAnalyticsReporter(JacksonMapper mapper) {
        return new LogAnalyticsReporter(mapper);
    }

    @Configuration
    @ConditionalOnProperty(prefix = "analytics.pubstack", name = "enabled", havingValue = "true")
    public static class PubstackAnalyticsConfiguration {

        @Bean
        PubstackAnalyticsReporter pubstackAnalyticsReporter(
                PubstackAnalyticsConfiguratinProperties pubstackAnalyticsConfiguratinProperties,
                HttpClient httpClient,
                JacksonMapper jacksonMapper,
                Vertx vertx) {

            return new PubstackAnalyticsReporter(
                    pubstackAnalyticsConfiguratinProperties.toComponentProperties(),
                    httpClient,
                    jacksonMapper,
                    vertx);
        }

        @Bean
        @ConfigurationProperties(prefix = "analytics.pubstack")
        PubstackAnalyticsConfiguratinProperties pubstackAnalyticsConfiguratinProperties() {
            return new PubstackAnalyticsConfiguratinProperties();
        }

        @Validated
        @NoArgsConstructor
        @Data
        private static class PubstackAnalyticsConfiguratinProperties {
            @NotNull
            String endpoint;

            @NotNull
            String scopeid;

            @NotNull
            Boolean enabled;

            @NotNull
            Long configurationRefreshDelayMs;

            @NotNull
            Long timeoutMs;

            @NotNull
            PubstackBufferProperties buffers;

            public PubstackAnalyticsProperties toComponentProperties() {
                return PubstackAnalyticsProperties.builder()
                        .endpoint(getEndpoint())
                        .scopeId(getScopeid())
                        .enabled(getEnabled())
                        .configurationRefreshDelayMs(getConfigurationRefreshDelayMs())
                        .sizeBytes(getBuffers().getSizeBytes())
                        .count(getBuffers().getCount())
                        .timeoutMs(getTimeoutMs())
                        .reportTtlMs(getBuffers().getReportTtlMs())
                        .build();
            }
        }

        @Validated
        @Data
        @NoArgsConstructor
        private static class PubstackBufferProperties {
            @NotNull
            Integer sizeBytes;

            @NotNull
            Integer count;

            @NotNull
            Long reportTtlMs;
        }
    }
}
