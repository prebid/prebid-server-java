package org.prebid.server.spring.config.metrics;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.micrometer.core.instrument.Clock;
import io.micrometer.influx.InfluxConfig;
import io.micrometer.influx.InfluxMeterRegistry;

@Configuration
public class InfluxRegistryConfiguration extends RegistryConfiguration implements InfluxConfig {

    @Bean
    @ConditionalOnProperty(prefix = "metrics.influx", name = "enabled", havingValue = "true")
    InfluxMeterRegistry influxMeterRegistry() {
        InfluxMeterRegistry influxMeterRegistry = new InfluxMeterRegistry(this, Clock.SYSTEM);

        this.addToComposite(influxMeterRegistry);

        return influxMeterRegistry;
    }
}
