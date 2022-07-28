package org.prebid.server.spring.config.metrics;

import org.prebid.server.spring.env.YamlPropertySourceFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.core.instrument.config.MeterRegistryConfig;

@Configuration
@PropertySource(value = "classpath:/metrics-config/metrics.yaml", factory = YamlPropertySourceFactory.class)
public abstract class RegistryConfiguration implements MeterRegistryConfig {

    @Autowired
    CompositeMeterRegistry compositeMeterRegistry;

    @Autowired
    Environment environment;

    @Override
    public String get(String key) {
        return environment.getProperty("metrics." + key);
    }

    protected final void addToComposite(MeterRegistry meterRegistry) {
        compositeMeterRegistry.add(meterRegistry);
    }
}
