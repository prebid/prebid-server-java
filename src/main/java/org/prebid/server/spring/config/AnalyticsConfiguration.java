package org.prebid.server.spring.config;

import io.vertx.core.Vertx;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.reporter.AnalyticsReporterDelegator;
import org.prebid.server.analytics.reporter.log.LogAnalyticsReporter;
import org.prebid.server.analytics.reporter.pubstack.PubstackAnalyticsReporter;
import org.prebid.server.analytics.reporter.pubstack.model.PubstackAnalyticsProperties;
import org.prebid.server.auction.privacy.enforcement.TcfEnforcement;
import org.prebid.server.auction.privacy.enforcement.mask.UserFpdActivityMask;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.metric.Metrics;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import java.util.List;

@Configuration
public class AnalyticsConfiguration {

    @Bean
    AnalyticsReporterDelegator analyticsReporterDelegator(
            Vertx vertx,
            @Autowired(required = false) List<AnalyticsReporter> delegates,
            TcfEnforcement tcfEnforcement,
            UserFpdActivityMask userFpdActivityMask,
            Metrics metrics,
            @Value("${logging.sampling-rate:0.01}") double logSamplingRate) {

        return new AnalyticsReporterDelegator(
                vertx,
                ListUtils.emptyIfNull(delegates),
                tcfEnforcement,
                userFpdActivityMask,
                metrics,
                logSamplingRate);
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
