package org.prebid.server.spring.config;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.file.FileSystem;
import org.prebid.server.vertx.ContextRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VertxConfiguration {

    @Bean
    Vertx vertx(@Value("${vertx.worker-pool-size}") Integer workerPoolSize) {
        return Vertx.vertx(new VertxOptions().setWorkerPoolSize(workerPoolSize));
    }

    @Bean
    FileSystem fileSystem(Vertx vertx) {
        return vertx.fileSystem();
    }

    @Bean
    ContextRunner contextRunner(Vertx vertx, @Value("${vertx.verticle.deploy-timeout-ms}") long initTimeoutMs) {
        return new ContextRunner(vertx, initTimeoutMs);
    }
}
