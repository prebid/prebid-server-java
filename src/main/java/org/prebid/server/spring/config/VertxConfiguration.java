package org.prebid.server.spring.config;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.file.FileSystem;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.dropwizard.DropwizardMetricsOptions;
import io.vertx.ext.dropwizard.Match;
import io.vertx.ext.dropwizard.MatchType;
import io.vertx.ext.web.handler.BodyHandler;
import org.prebid.server.spring.config.metrics.MetricsConfiguration;
import org.prebid.server.vertx.ContextRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VertxConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(VertxConfiguration.class);

    @Bean
    Vertx vertx(@Value("${vertx.worker-pool-size}") int workerPoolSize,
                @Value("${vertx.enable-per-client-endpoint-metrics}") boolean enablePerClientEndpointMetrics,
                @Value("${metrics.jmx.enabled}") boolean jmxEnabled) {
        final DropwizardMetricsOptions metricsOptions = new DropwizardMetricsOptions()
                .setEnabled(true)
                .setJmxEnabled(jmxEnabled)
                .setRegistryName(MetricsConfiguration.METRIC_REGISTRY_NAME);
        if (enablePerClientEndpointMetrics) {
            metricsOptions.addMonitoredHttpClientEndpoint(new Match().setValue(".*").setType(MatchType.REGEX));
        }

        final VertxOptions vertxOptions = new VertxOptions()
                .setPreferNativeTransport(true)
                .setWorkerPoolSize(workerPoolSize)
                .setMetricsOptions(metricsOptions);

        final Vertx vertx = Vertx.vertx(vertxOptions);
        logger.info("Native transport enabled: {0}", vertx.isNativeTransportEnabled());
        return vertx;
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
