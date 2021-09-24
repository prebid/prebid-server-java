package org.prebid.server.spring.config;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.file.FileSystem;
import io.vertx.ext.dropwizard.DropwizardMetricsOptions;
import io.vertx.ext.dropwizard.Match;
import io.vertx.ext.dropwizard.MatchType;
import io.vertx.ext.web.handler.BodyHandler;
import org.prebid.server.vertx.ContextRunner;
import org.prebid.server.vertx.LocalMessageCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VertxConfiguration {

    @Bean
    Vertx vertx(@Value("${vertx.worker-pool-size}") int workerPoolSize,
                @Value("${vertx.enable-per-client-endpoint-metrics}") boolean enablePerClientEndpointMetrics) {
        final DropwizardMetricsOptions metricsOptions = new DropwizardMetricsOptions()
                .setEnabled(true)
                .setRegistryName(MetricsConfiguration.METRIC_REGISTRY_NAME);
        if (enablePerClientEndpointMetrics) {
            metricsOptions.addMonitoredHttpClientEndpoint(new Match().setValue(".*").setType(MatchType.REGEX));
        }

        final VertxOptions vertxOptions = new VertxOptions()
                .setWorkerPoolSize(workerPoolSize)
                .setMetricsOptions(metricsOptions);

        return Vertx.vertx(vertxOptions);
    }

    @Bean
    EventBus eventBus(Vertx vertx) {
        final EventBus eventBus = vertx.eventBus();
        eventBus.registerCodec(LocalMessageCodec.create());

        return eventBus;
    }

    @Bean
    FileSystem fileSystem(Vertx vertx) {
        return vertx.fileSystem();
    }

    @Bean
    BodyHandler bodyHandler(@Value("${vertx.uploads-dir}") String uploadsDir) {
        return BodyHandler.create(uploadsDir);
    }

    @Bean
    ContextRunner contextRunner(Vertx vertx, @Value("${vertx.init-timeout-ms}") long initTimeoutMs) {
        return new ContextRunner(vertx, initTimeoutMs);
    }
}
