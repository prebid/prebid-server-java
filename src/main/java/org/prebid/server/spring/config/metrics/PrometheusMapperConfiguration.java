package org.prebid.server.spring.config.metrics;

import io.prometheus.client.dropwizard.samplebuilder.MapperConfig;
import lombok.Data;
import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

@Configuration
@ConditionalOnProperty(value = "metrics.prometheus.custom-labels-enabled", havingValue = "true")
@PropertySource(value = "classpath:/metrics-config/prometheus-labels.yaml", factory = YamlPropertySourceFactory.class)
public class PrometheusMapperConfiguration {

    @Bean
    public List<MapperConfig> mapperConfigs(PrometheusMappersProperties prometheusMappersProperties) {
        return prometheusMappersProperties.getMappers().stream()
                .map(mapperProperties -> new MapperConfig(
                        mapperProperties.getMatch(),
                        mapperProperties.getName(),
                        mapperProperties.getLabels()))
                .toList();
    }

    @Data
    @Component
    @Validated
    @ConditionalOnProperty(value = "metrics.prometheus.custom-labels-enabled", havingValue = "true")
    @ConfigurationProperties
    public static class PrometheusMappersProperties {

        @NotNull
        List<PrometheusLabelsMapperProperties> mappers;
    }

    @Data
    @Validated
    public static class PrometheusLabelsMapperProperties {

        @NotNull
        String match;

        @NotNull
        String name;

        Map<String, String> labels;
    }
}
