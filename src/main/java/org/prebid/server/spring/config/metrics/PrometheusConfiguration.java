package org.prebid.server.spring.config.metrics;

import com.codahale.metrics.MetricRegistry;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.dropwizard.DropwizardExports;
import io.prometheus.client.dropwizard.samplebuilder.CustomMappingSampleBuilder;
import io.prometheus.client.dropwizard.samplebuilder.DefaultSampleBuilder;
import io.prometheus.client.dropwizard.samplebuilder.MapperConfig;
import io.prometheus.client.dropwizard.samplebuilder.SampleBuilder;
import io.prometheus.client.vertx.MetricsHandler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.prebid.server.metric.prometheus.NamespaceSubsystemSampleBuilder;
import org.prebid.server.vertx.ContextRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.annotation.PostConstruct;
import javax.validation.constraints.NotNull;
import java.util.List;

@Configuration
public class PrometheusConfiguration {

    @Bean
    @ConditionalOnBean(PrometheusConfigurationProperties.class)
    public SampleBuilder sampleBuilder(PrometheusConfigurationProperties prometheusConfigurationProperties,
                                       List<MapperConfig> mapperConfigs) {

        final SampleBuilder sampleBuilder = mapperConfigs.isEmpty()
                ? new DefaultSampleBuilder()
                : new CustomMappingSampleBuilder(mapperConfigs);

        return new NamespaceSubsystemSampleBuilder(
                sampleBuilder,
                prometheusConfigurationProperties.getNamespace(),
                prometheusConfigurationProperties.getSubsystem());
    }

    @Configuration
    @ConditionalOnBean(PrometheusConfigurationProperties.class)
    public static class PrometheusServerConfiguration {
        private static final Logger logger = LoggerFactory.getLogger(PrometheusServerConfiguration.class);

        @Autowired
        private ContextRunner contextRunner;

        @Autowired
        private Vertx vertx;

        @Autowired
        private MetricRegistry metricRegistry;

        @Autowired
        private PrometheusConfigurationProperties prometheusConfigurationProperties;

        @Autowired
        private SampleBuilder sampleBuilder;

        @PostConstruct
        public void startPrometheusServer() {
            logger.info(
                    "Starting Prometheus Server on port {0,number,#}",
                    prometheusConfigurationProperties.getPort());

            final Router router = Router.router(vertx);
            router.route("/metrics").handler(new MetricsHandler());

            CollectorRegistry.defaultRegistry.register(new DropwizardExports(metricRegistry, sampleBuilder));

            contextRunner.<HttpServer>runOnServiceContext(promise ->
                    vertx.createHttpServer()
                            .requestHandler(router)
                            .listen(prometheusConfigurationProperties.getPort(), promise));

            logger.info("Successfully started Prometheus Server");
        }
    }

    @Data
    @Validated
    @Component
    @NoArgsConstructor
    @ConfigurationProperties(prefix = "metrics.prometheus")
    @ConditionalOnProperty(prefix = "metrics.prometheus", name = "enabled", havingValue = "true")
    public static class PrometheusConfigurationProperties {

        @NotNull
        Integer port;

        String namespace;

        String subsystem;
    }
}
