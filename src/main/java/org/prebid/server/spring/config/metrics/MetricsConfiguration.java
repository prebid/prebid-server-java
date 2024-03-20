package org.prebid.server.spring.config.metrics;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.SharedMetricRegistries;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;
import com.codahale.metrics.jvm.GarbageCollectorMetricSet;
import com.codahale.metrics.jvm.MemoryUsageGaugeSet;
import com.izettle.metrics.influxdb.InfluxDbHttpSender;
import com.izettle.metrics.influxdb.InfluxDbReporter;
import com.izettle.metrics.influxdb.InfluxDbSender;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.metric.AccountMetricsVerbosityResolver;
import org.prebid.server.metric.CounterType;
import org.prebid.server.metric.Metrics;
import org.prebid.server.metric.model.AccountMetricsVerbosityLevel;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Configuration
@PropertySource(value = "classpath:/metrics-config/metrics.yaml", factory = YamlPropertySourceFactory.class)
public class MetricsConfiguration {

    public static final String METRIC_REGISTRY_NAME = "metric-registry";

    @Bean
    @ConditionalOnProperty(prefix = "metrics.graphite", name = "enabled", havingValue = "true")
    ScheduledReporter graphiteReporter(GraphiteProperties graphiteProperties, MetricRegistry metricRegistry) {
        final Graphite graphite = new Graphite(graphiteProperties.getHost(), graphiteProperties.getPort());
        final ScheduledReporter reporter = GraphiteReporter.forRegistry(metricRegistry)
                .prefixedWith(graphiteProperties.getPrefix())
                .build(graphite);
        reporter.start(graphiteProperties.getInterval(), TimeUnit.SECONDS);

        return reporter;
    }

    @Bean
    @ConditionalOnProperty(prefix = "metrics.influxdb", name = "enabled", havingValue = "true")
    ScheduledReporter influxdbReporter(InfluxdbProperties influxdbProperties, MetricRegistry metricRegistry)
            throws Exception {
        final InfluxDbSender influxDbSender = new InfluxDbHttpSender(
                influxdbProperties.getProtocol(),
                influxdbProperties.getHost(),
                influxdbProperties.getPort(),
                influxdbProperties.getDatabase(),
                influxdbProperties.getAuth(),
                TimeUnit.SECONDS,
                influxdbProperties.getConnectTimeout(),
                influxdbProperties.getReadTimeout(),
                influxdbProperties.getPrefix());
        final Map<String, String> tags = ObjectUtils.defaultIfNull(
                influxdbProperties.getTags(),
                Collections.emptyMap()
        );
        final ScheduledReporter reporter = InfluxDbReporter
                .forRegistry(metricRegistry)
                .withTags(tags)
                .build(influxDbSender);
        reporter.start(influxdbProperties.getInterval(), TimeUnit.SECONDS);

        return reporter;
    }

    @Bean
    @ConditionalOnProperty(prefix = "metrics.console", name = "enabled", havingValue = "true")
    ScheduledReporter consoleReporter(ConsoleProperties consoleProperties, MetricRegistry metricRegistry) {
        final ScheduledReporter reporter = ConsoleReporter.forRegistry(metricRegistry).build();
        reporter.start(consoleProperties.getInterval(), TimeUnit.SECONDS);

        return reporter;
    }

    @Bean
    Metrics metrics(@Value("${metrics.metricType}") CounterType counterType, MetricRegistry metricRegistry,
                    AccountMetricsVerbosityResolver accountMetricsVerbosityResolver) {
        return new Metrics(metricRegistry, counterType, accountMetricsVerbosityResolver);
    }

    @Bean
    MetricRegistry metricRegistry(@Value("${metrics.jmx.enabled}") boolean jmxEnabled) {
        final boolean alreadyExists = SharedMetricRegistries.names().contains(METRIC_REGISTRY_NAME);
        final MetricRegistry metricRegistry = SharedMetricRegistries.getOrCreate(METRIC_REGISTRY_NAME);

        if (!alreadyExists && jmxEnabled) {
            metricRegistry.register("jvm.gc", new GarbageCollectorMetricSet());
            metricRegistry.register("jvm.memory", new MemoryUsageGaugeSet());
        }

        return metricRegistry;
    }

    @Bean
    AccountMetricsVerbosityResolver accountMetricsVerbosity(AccountsProperties accountsProperties) {
        return new AccountMetricsVerbosityResolver(
                accountsProperties.getDefaultVerbosity(),
                accountsProperties.getBasicVerbosity(),
                accountsProperties.getDetailedVerbosity());
    }

    @Component
    @ConfigurationProperties(prefix = "metrics.graphite")
    @ConditionalOnProperty(prefix = "metrics.graphite", name = "enabled", havingValue = "true")
    @Validated
    @Data
    @NoArgsConstructor
    private static class GraphiteProperties {

        @NotBlank
        private String prefix;
        @NotBlank
        private String host;
        @NotNull
        private Integer port;
        @NotNull
        @Min(1)
        private Integer interval;
    }

    @Component
    @ConfigurationProperties(prefix = "metrics.influxdb")
    @ConditionalOnProperty(prefix = "metrics.influxdb", name = "enabled", havingValue = "true")
    @Validated
    @Data
    @NoArgsConstructor
    private static class InfluxdbProperties {

        @NotBlank
        private String prefix;
        @NotBlank
        private String protocol;
        @NotBlank
        private String host;
        @NotNull
        private Integer port;
        @NotBlank
        private String database;
        @NotBlank
        private String auth;
        @NotNull
        @Min(1)
        private Integer connectTimeout;
        @NotNull
        @Min(1)
        private Integer readTimeout;
        @NotNull
        @Min(1)
        private Integer interval;
        private Map<String, String> tags;
    }

    @Component
    @ConfigurationProperties(prefix = "metrics.console")
    @ConditionalOnProperty(prefix = "metrics.console", name = "enabled", havingValue = "true")
    @Validated
    @Data
    @NoArgsConstructor
    private static class ConsoleProperties {

        @NotNull
        @Min(1)
        private Integer interval;
    }

    @Component
    @ConfigurationProperties(prefix = "metrics.accounts")
    @Validated
    @Data
    @NoArgsConstructor
    private static class AccountsProperties {

        @NotNull
        private AccountMetricsVerbosityLevel defaultVerbosity;
        private List<String> basicVerbosity = new ArrayList<>();
        private List<String> detailedVerbosity = new ArrayList<>();
    }
}
