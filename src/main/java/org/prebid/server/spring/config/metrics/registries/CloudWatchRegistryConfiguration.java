package org.prebid.server.spring.config.metrics.registries;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.services.cloudwatch.CloudWatchAsyncClient;
import io.micrometer.cloudwatch2.CloudWatchConfig;
import io.micrometer.cloudwatch2.CloudWatchMeterRegistry;
import io.micrometer.core.instrument.Clock;

@Configuration
public class CloudWatchRegistryConfiguration extends RegistryConfiguration implements CloudWatchConfig {

    @Bean
    @ConditionalOnProperty(prefix = "metrics.cloudwatch", name = "enabled", havingValue = "true")
    CloudWatchMeterRegistry cloudWatchMeterRegistry() {
        CloudWatchMeterRegistry cloudWatchMeterRegistry = new CloudWatchMeterRegistry(
                this, Clock.SYSTEM, CloudWatchAsyncClient.create());

        this.addToComposite(cloudWatchMeterRegistry);

        return cloudWatchMeterRegistry;
    }
}
