package org.prebid.server.spring.config.metrics.registries;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.micrometer.core.instrument.Clock;
import io.micrometer.graphite.GraphiteConfig;
import io.micrometer.graphite.GraphiteMeterRegistry;
import io.micrometer.core.instrument.util.HierarchicalNameMapper;

@Configuration
public class GraphiteRegistryConfiguration extends RegistryConfiguration implements GraphiteConfig {

    @Bean
    @ConditionalOnProperty(prefix = "metrics.graphite", name = "enabled", havingValue = "true")
    GraphiteMeterRegistry graphiteMeterRegistry() {
        GraphiteMeterRegistry graphiteMeterRegistry = new GraphiteMeterRegistry(
                this, Clock.SYSTEM, HierarchicalNameMapper.DEFAULT);

        this.addToComposite(graphiteMeterRegistry);

        return graphiteMeterRegistry;
    }
}
