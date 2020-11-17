package org.prebid.server.spring.config;

import io.vertx.core.Vertx;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.CompositeAnalyticsReporter;
import org.prebid.server.analytics.LogAnalyticsReporter;
import org.prebid.server.analytics.pubstack.PubstackAnalyticsReporter;
import org.prebid.server.analytics.pubstack.PubstackEventHandler;
import org.prebid.server.analytics.pubstack.model.EventType;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.http.HttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configuration
public class AnalyticsConfiguration {

    @Bean
    CompositeAnalyticsReporter compositeAnalyticsReporter(
            @Autowired(required = false) List<AnalyticsReporter> delegates, Vertx vertx) {

        return new CompositeAnalyticsReporter(delegates != null ? delegates : Collections.emptyList(), vertx);
    }

    @Bean
    @ConditionalOnProperty(prefix = "analytics.log", name = "enabled", havingValue = "true")
    LogAnalyticsReporter logAnalyticsReporter(JacksonMapper mapper) {
        return new LogAnalyticsReporter(mapper);
    }

    @Configuration
    @ConditionalOnProperty(prefix = "analytics.pubstack", name = "enabled", havingValue = "true")
    public static class PubstackAnalyticsConfiguration {

        private static final String EVENT_REPORT_ENDPOINT_PATH = "/intake";

        @Bean
        PubstackAnalyticsReporter pubstackAnalyticsReporter(PubstackAnalyticsProperties pubstackAnalyticsProperties,
                                                            HttpClient httpClient,
                                                            JacksonMapper jacksonMapper,
                                                            Vertx vertx) {
            final org.prebid.server.analytics.pubstack.model.PubstackAnalyticsProperties properties =
                    pubstackAnalyticsProperties.toComponentProperties();
            final Map<EventType, PubstackEventHandler> eventHandlers = Arrays.stream(EventType.values())
                    .collect(Collectors.toMap(Function.identity(),
                            eventType -> new PubstackEventHandler(
                                    properties,
                                    false,
                                    buildEventEndpointUrl(pubstackAnalyticsProperties, eventType),
                                    jacksonMapper,
                                    httpClient,
                                    vertx)));

            return new PubstackAnalyticsReporter(
                    properties,
                    eventHandlers,
                    httpClient,
                    jacksonMapper,
                    vertx);
        }

        private String buildEventEndpointUrl(PubstackAnalyticsProperties pubstackAnalyticsProperties,
                                             EventType eventType) {
            return HttpUtil.validateUrl(pubstackAnalyticsProperties.getEndpoint()
                    + EVENT_REPORT_ENDPOINT_PATH + eventType.name());
        }

        @Bean
        @ConfigurationProperties(prefix = "analytics.pubstack")
        PubstackAnalyticsProperties pubstackAnalyticsProperties() {
            return new PubstackAnalyticsProperties();
        }

        @Validated
        @NoArgsConstructor
        @Data
        private static class PubstackAnalyticsProperties {
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

            public org.prebid.server.analytics.pubstack.model.PubstackAnalyticsProperties toComponentProperties() {
                return org.prebid.server.analytics.pubstack.model.PubstackAnalyticsProperties.builder()
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
