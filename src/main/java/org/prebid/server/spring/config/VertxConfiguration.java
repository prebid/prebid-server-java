package org.prebid.server.spring.config;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.file.FileSystem;
import io.vertx.ext.dropwizard.DropwizardMetricsOptions;
import io.vertx.ext.web.handler.BodyHandler;
import org.prebid.server.vertx.ContextRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VertxConfiguration {

    @Bean
    Vertx vertx(@Value("${vertx.worker-pool-size}") int workerPoolSize) {
        return Vertx.vertx(new VertxOptions()
                .setWorkerPoolSize(workerPoolSize)
                .setMetricsOptions(new DropwizardMetricsOptions()
                        .setEnabled(true)
                        .setRegistryName(MetricsConfiguration.METRIC_REGISTRY_NAME)));
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
