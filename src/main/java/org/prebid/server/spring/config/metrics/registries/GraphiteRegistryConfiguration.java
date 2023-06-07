package org.prebid.server.spring.config.metrics.registries;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.config.NamingConvention;
import io.micrometer.graphite.GraphiteConfig;
import io.micrometer.graphite.GraphiteMeterRegistry;
import org.prebid.server.spring.config.metrics.PrefixedNamingConvention;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "metrics.graphite", name = "enabled", havingValue = "true")
public class GraphiteRegistryConfiguration extends RegistryConfiguration implements GraphiteConfig {

    @Bean
    public GraphiteMeterRegistry graphiteMeterRegistry() {
        final GraphiteMeterRegistry graphiteMeterRegistry = new GraphiteMeterRegistry(this, Clock.SYSTEM);
        final NamingConvention namingConvention = new PrefixedNamingConvention(
                get("graphite.prefix"), NamingConvention.identity);
        graphiteMeterRegistry.config().namingConvention(namingConvention);
        return graphiteMeterRegistry;
    }
}
