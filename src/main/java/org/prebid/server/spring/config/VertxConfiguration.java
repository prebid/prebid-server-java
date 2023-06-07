package org.prebid.server.spring.config;

import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.file.FileSystem;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.micrometer.MicrometerMetricsOptions;
import org.prebid.server.vertx.ContextRunner;
import org.prebid.server.vertx.LocalMessageCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class VertxConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(VertxConfiguration.class);

    @Bean
    Vertx vertx(@Value("${vertx.worker-pool-size}") int workerPoolSize,
                CompositeMeterRegistry meterRegistry) {
        final MicrometerMetricsOptions micrometerMetricsOptions = new MicrometerMetricsOptions()
                .setEnabled(true)
                .setMicrometerRegistry(meterRegistry);
//                .setPrometheusOptions(
//                        new VertxPrometheusOptions()
//                                .setEnabled(true)
//                                .setStartEmbeddedServer(true)
//                                .setEmbeddedServerOptions(new HttpServerOptions().setPort(8090))
//                                .setEmbeddedServerEndpoint("/metrics/vertx"));

        final VertxOptions vertxOptions = new VertxOptions()
                .setPreferNativeTransport(true)
                .setWorkerPoolSize(workerPoolSize)
                .setMetricsOptions(micrometerMetricsOptions);

        final Vertx vertx = Vertx.vertx(vertxOptions);
        logger.info("Native transport enabled: {0}", vertx.isNativeTransportEnabled());
        return vertx;
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
