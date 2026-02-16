package org.prebid.server.spring.config;

import io.vertx.core.Vertx;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.reporter.AnalyticsReporterDelegator;
import org.prebid.server.analytics.reporter.agma.AgmaAnalyticsReporter;
import org.prebid.server.analytics.reporter.agma.model.AgmaAnalyticsProperties;
import org.prebid.server.analytics.reporter.greenbids.GreenbidsAnalyticsReporter;
import org.prebid.server.analytics.reporter.greenbids.model.GreenbidsAnalyticsProperties;
import org.prebid.server.analytics.reporter.liveintent.LiveIntentAnalyticsReporter;
import org.prebid.server.analytics.reporter.liveintent.model.LiveIntentAnalyticsProperties;
import org.prebid.server.analytics.reporter.log.LogAnalyticsReporter;
import org.prebid.server.analytics.reporter.pubstack.PubstackAnalyticsReporter;
import org.prebid.server.analytics.reporter.pubstack.model.PubstackAnalyticsProperties;
import org.prebid.server.auction.privacy.enforcement.TcfEnforcement;
import org.prebid.server.auction.privacy.enforcement.mask.UserFpdActivityMask;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.metric.Metrics;
import org.prebid.server.version.PrebidVersionProvider;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
public class AnalyticsConfiguration {

    @Bean
    AnalyticsReporterDelegator analyticsReporterDelegator(
            Vertx vertx,
            @Autowired(required = false) List<AnalyticsReporter> delegates,
            TcfEnforcement tcfEnforcement,
            UserFpdActivityMask userFpdActivityMask,
            Metrics metrics,
            @Value("${logging.sampling-rate:0.01}") double logSamplingRate,
            @Value("${analytics.global.adapters}") Set<String> globalEnabledAdapters,
            JacksonMapper mapper) {

        return new AnalyticsReporterDelegator(
                vertx,
                ListUtils.emptyIfNull(delegates),
                tcfEnforcement,
                userFpdActivityMask,
                metrics,
                logSamplingRate,
                globalEnabledAdapters,
                mapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "analytics.log", name = "enabled", havingValue = "true")
    LogAnalyticsReporter logAnalyticsReporter(JacksonMapper mapper) {
        return new LogAnalyticsReporter(mapper);
    }

    @Configuration
    @ConditionalOnProperty(prefix = "analytics.agma", name = "enabled", havingValue = "true")
    public static class AgmaAnalyticsConfiguration {

        @Bean
        AgmaAnalyticsReporter agmaAnalyticsReporter(AgmaAnalyticsConfigurationProperties properties,
                                                    JacksonMapper jacksonMapper,
                                                    HttpClient httpClient,
                                                    Clock clock,
                                                    PrebidVersionProvider prebidVersionProvider,
                                                    Vertx vertx) {

            return new AgmaAnalyticsReporter(
                    properties.toComponentProperties(),
                    prebidVersionProvider,
                    jacksonMapper,
                    clock,
                    httpClient,
                    vertx);
        }

        @Bean
        @ConfigurationProperties(prefix = "analytics.agma")
        AgmaAnalyticsConfigurationProperties agmaAnalyticsConfigurationProperties() {
            return new AgmaAnalyticsConfigurationProperties();
        }

        @Validated
        @NoArgsConstructor
        @Data
        private static class AgmaAnalyticsConfigurationProperties {

            @NotNull
            private AgmaAnalyticsHttpEndpointProperties endpoint;

            @NotNull
            private AgmaAnalyticsBufferProperties buffers;

            @NotEmpty(message = "Please configure at least one account for Agma Analytics")
            private List<AgmaAnalyticsAccountProperties> accounts;

            public AgmaAnalyticsProperties toComponentProperties() {
                final Map<String, String> accountsByPublisherId = accounts.stream()
                        .collect(Collectors.toMap(
                                this::buildPublisherSiteAppIdKey,
                                AgmaAnalyticsAccountProperties::getCode
                        ));

                return AgmaAnalyticsProperties.builder()
                        .url(endpoint.getUrl())
                        .gzip(BooleanUtils.isTrue(endpoint.getGzip()))
                        .bufferSize(buffers.getSizeBytes())
                        .maxEventsCount(buffers.getCount())
                        .bufferTimeoutMs(buffers.getTimeoutMs())
                        .httpTimeoutMs(endpoint.getTimeoutMs())
                        .accounts(accountsByPublisherId)
                        .build();
            }

            private String buildPublisherSiteAppIdKey(AgmaAnalyticsAccountProperties account) {
                final String publisherId = account.getPublisherId();
                final String siteAppId = account.getSiteAppId();
                return StringUtils.isNotBlank(siteAppId)
                        ? String.format("%s_%s", publisherId, siteAppId)
                        : publisherId;
            }

            @Validated
            @NoArgsConstructor
            @Data
            private static class AgmaAnalyticsHttpEndpointProperties {

                @NotNull
                private String url;

                @NotNull
                private Long timeoutMs;

                private Boolean gzip;
            }

            @NoArgsConstructor
            @Data
            private static class AgmaAnalyticsBufferProperties {

                @NotNull
                private Integer sizeBytes;

                @NotNull
                private Integer count;

                @NotNull
                private Long timeoutMs;
            }

            @NoArgsConstructor
            @Data
            private static class AgmaAnalyticsAccountProperties {

                private String code;

                @NotNull
                private String publisherId;

                private String siteAppId;
            }
        }
    }

    @Configuration
    @ConditionalOnProperty(prefix = "analytics.greenbids", name = "enabled", havingValue = "true")
    public static class GreenbidsAnalyticsConfiguration {

        @Bean
        GreenbidsAnalyticsReporter greenbidsAnalyticsReporter(
                GreenbidsAnalyticsConfigurationProperties greenbidsAnalyticsConfigurationProperties,
                JacksonMapper jacksonMapper,
                HttpClient httpClient,
                Clock clock,
                PrebidVersionProvider prebidVersionProvider) {
            return new GreenbidsAnalyticsReporter(
                    greenbidsAnalyticsConfigurationProperties.toComponentProperties(),
                    jacksonMapper,
                    httpClient,
                    clock,
                    prebidVersionProvider);
        }

        @Bean
        @ConfigurationProperties(prefix = "analytics.greenbids")
        GreenbidsAnalyticsConfigurationProperties greenbidsAnalyticsConfigurationProperties() {
            return new GreenbidsAnalyticsConfigurationProperties();
        }

        @Validated
        @NoArgsConstructor
        @Data
        private static class GreenbidsAnalyticsConfigurationProperties {
            String analyticsServerVersion;

            String analyticsServer;

            Double exploratorySamplingSplit;

            Double defaultSamplingRate;

            Long timeoutMs;

            public GreenbidsAnalyticsProperties toComponentProperties() {
                return GreenbidsAnalyticsProperties.builder()
                        .exploratorySamplingSplit(getExploratorySamplingSplit())
                        .defaultSamplingRate(getDefaultSamplingRate())
                        .analyticsServerVersion(getAnalyticsServerVersion())
                        .analyticsServerUrl(getAnalyticsServer())
                        .timeoutMs(getTimeoutMs())
                        .build();
            }
        }
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

    @Configuration
    @ConditionalOnProperty(prefix = "analytics.liveintent", name = "enabled", havingValue = "true")
    public static class LiveIntentAnalyticsConfiguration {

        @Bean
        LiveIntentAnalyticsReporter liveIntentAnalyticsReporter(
                LiveIntentAnalyticsConfigurationProperties properties,
                HttpClient httpClient,
                JacksonMapper jacksonMapper) {

            return new LiveIntentAnalyticsReporter(
                    properties.toComponentProperties(),
                    httpClient,
                    jacksonMapper);
        }

        @Bean
        @ConfigurationProperties(prefix = "analytics.liveintent")
        LiveIntentAnalyticsConfigurationProperties liveIntentAnalyticsConfigurationProperties() {
            return new LiveIntentAnalyticsConfigurationProperties();
        }

        @Validated
        @NoArgsConstructor
        @Data
        private static class LiveIntentAnalyticsConfigurationProperties {

            String partnerId;

            String analyticsEndpoint;

            long timeoutMs;

            public LiveIntentAnalyticsProperties toComponentProperties() {
                return LiveIntentAnalyticsProperties.builder()
                        .partnerId(this.partnerId)
                        .analyticsEndpoint(this.analyticsEndpoint)
                        .timeoutMs(this.timeoutMs)
                        .build();
            }
        }
    }
}
