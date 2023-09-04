package org.prebid.server.spring.config.metrics;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.graphite.GraphiteMeterRegistry;
import io.micrometer.jmx.JmxMeterRegistry;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.prebid.server.metric.AccountMetricsVerbosityResolver;
import org.prebid.server.metric.CounterType;
import org.prebid.server.metric.Metrics;
import org.prebid.server.metric.model.AccountMetricsVerbosityLevel;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

@Configuration
@PropertySource(value = "classpath:/metrics-config/metrics.yaml", factory = YamlPropertySourceFactory.class)
public class MetricsConfiguration {

    @Autowired
    Environment environment;

    @Bean
    CompositeMeterRegistry compositeMeterRegistry(List<MeterRegistry> meterRegistries) {
        CompositeMeterRegistry registry = new CompositeMeterRegistry();
        meterRegistries.forEach(registry::add);

        return registry;
    }

    @Bean
    MeterRegistry graphiteMeterRegistry() {
        GraphiteMeterRegistry test = new GraphiteMeterRegistry(this::extractProperty, Clock.SYSTEM);
        test.config().namingConvention(NamingConvention.identity);
        return test;
    }

    @Bean
    MeterRegistry prometheusMeterRegistry() {
        return new PrometheusMeterRegistry(this::extractProperty);
    }

    @Bean
    MeterRegistry jmxMeterRegistry() {
        return new JmxMeterRegistry(this::extractProperty, Clock.SYSTEM);
    }

    @Bean
    Metrics metrics(@Value("${metrics.metricType}") CounterType counterType,
                    CompositeMeterRegistry meterRegistry,
                    AccountMetricsVerbosityResolver accountMetricsVerbosityResolver) {

        return new Metrics(meterRegistry, counterType, accountMetricsVerbosityResolver);
    }

    @Bean
    AccountMetricsVerbosityResolver accountMetricsVerbosity(AccountsProperties accountsProperties) {
        return new AccountMetricsVerbosityResolver(
                accountsProperties.getDefaultVerbosity(),
                accountsProperties.getBasicVerbosity(),
                accountsProperties.getDetailedVerbosity());
    }

    private String extractProperty(String key) {
        return environment.getProperty("metrics." + key);
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
