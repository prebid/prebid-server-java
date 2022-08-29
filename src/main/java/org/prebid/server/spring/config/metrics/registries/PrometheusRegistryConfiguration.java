package org.prebid.server.spring.config.metrics.registries;

import org.prebid.server.vertx.ContextRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;

import javax.annotation.PostConstruct;

@Configuration
public class PrometheusRegistryConfiguration extends RegistryConfiguration implements PrometheusConfig {

    @Configuration
    @ConditionalOnProperty(prefix = "metrics.prometheus", name = "enabled", havingValue = "true")
    public static class PrometheusServerConfiguration {
        private static final Logger logger = LoggerFactory.getLogger(PrometheusServerConfiguration.class);

        @Autowired
        private ContextRunner contextRunner;

        @Autowired
        private Vertx vertx;

        @Autowired
        private PrometheusMeterRegistry prometheusMeterRegistry;

        @Value("${metrics.prometheus.path}")
        private String path;

        @Value("${metrics.prometheus.port}")
        private int port;

        @PostConstruct
        public void startPrometheusServer() {
            logger.info(
                    "Starting Prometheus Server on port {0,number,#}",
                    this.port);

            final Router router = Router.router(vertx);
            router.route(this.path).handler(ctx -> {
                HttpServerResponse response = ctx.response();
                response.end(prometheusMeterRegistry.scrape());
            });

            contextRunner.<HttpServer>runOnServiceContext(promise ->
                    vertx.createHttpServer()
                            .requestHandler(router)
                            .listen(this.port, promise));

            logger.info("Successfully started Prometheus Server");
        }
    }

    @Bean
    @ConditionalOnProperty(prefix = "metrics.prometheus", name = "enabled", havingValue = "true")
    PrometheusMeterRegistry prometheusMeterRegistry() {
        PrometheusMeterRegistry prometheusMeterRegistry = new PrometheusMeterRegistry(this);

        this.addToComposite(prometheusMeterRegistry);

        return prometheusMeterRegistry;
    }
}
