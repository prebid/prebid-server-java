package org.prebid.server.spring.config.metrics;

import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.prebid.server.metric.AccountMetricsVerbosityResolver;
import org.prebid.server.metric.Metrics;
import org.prebid.server.metric.model.AccountMetricsVerbosityLevel;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

@Configuration
@PropertySource(value = "classpath:/metrics-config/metrics.yaml", factory = YamlPropertySourceFactory.class)
public class MetricsConfiguration {

    @Bean
    CompositeMeterRegistry compositeMeterRegistry() {
        return new CompositeMeterRegistry();
    }

    @Bean
    Metrics metrics(
            CompositeMeterRegistry meterRegistry,
            AccountMetricsVerbosityResolver accountMetricsVerbosityResolver) {
        return new Metrics(meterRegistry, accountMetricsVerbosityResolver);
    }

    @Bean
    AccountMetricsVerbosityResolver accountMetricsVerbosity(AccountsProperties accountsProperties) {
        return new AccountMetricsVerbosityResolver(
                accountsProperties.getDefaultVerbosity(),
                accountsProperties.getBasicVerbosity(),
                accountsProperties.getDetailedVerbosity());
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
