package org.prebid.server.spring.config.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.Data;
import lombok.NoArgsConstructor;
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

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Configuration
@PropertySource(value = "classpath:/metrics-config/metrics.yaml", factory = YamlPropertySourceFactory.class)
public class MetricsConfiguration {

    @Bean
    Metrics metrics(@Value("${metrics.metricType}") CounterType counterType,
                    CompositeMeterRegistry compositeMeterRegistry,
                    AccountMetricsVerbosityResolver accountMetricsVerbosityResolver) {

        return new Metrics(compositeMeterRegistry, counterType, accountMetricsVerbosityResolver);
    }

    @Bean
    CompositeMeterRegistry compositeMeterRegistry(List<MeterRegistry> meterRegistries) {
        final CompositeMeterRegistry compositeMeterRegistry = new CompositeMeterRegistry()
                .add(new SimpleMeterRegistry());

        meterRegistries.forEach(compositeMeterRegistry::add);
        return compositeMeterRegistry;
    }

    @Bean
    AccountMetricsVerbosityResolver accountMetricsVerbosity(AccountsProperties accountsProperties) {
        return new AccountMetricsVerbosityResolver(
                accountsProperties.getDefaultVerbosity(),
                accountsProperties.getBasicVerbosity(),
                accountsProperties.getDetailedVerbosity());
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
