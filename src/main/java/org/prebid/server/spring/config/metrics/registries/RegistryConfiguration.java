package org.prebid.server.spring.config.metrics.registries;

import io.micrometer.core.instrument.config.MeterRegistryConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public abstract class RegistryConfiguration implements MeterRegistryConfig {

    @Autowired
    Environment environment;

    @Override
    public String get(String key) {
        return environment.getProperty("metrics." + key);
    }
}
