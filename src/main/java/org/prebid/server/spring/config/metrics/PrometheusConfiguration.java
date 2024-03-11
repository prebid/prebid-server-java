package org.prebid.server.spring.config.metrics;

import com.codahale.metrics.MetricRegistry;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.dropwizard.DropwizardExports;
import io.prometheus.client.dropwizard.samplebuilder.MapperConfig;
import io.prometheus.client.dropwizard.samplebuilder.SampleBuilder;
import io.prometheus.client.vertx.MetricsHandler;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.Router;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.prebid.server.metric.CounterType;
import org.prebid.server.metric.Metrics;
import org.prebid.server.metric.prometheus.NamespaceSubsystemSampleBuilder;
import org.prebid.server.vertx.verticles.VerticleDefinition;
import org.prebid.server.vertx.verticles.server.ServerVerticle;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotNull;
import java.util.List;

@Configuration
@ConditionalOnProperty(prefix = "metrics.prometheus", name = "enabled", havingValue = "true")
public class PrometheusConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(PrometheusConfiguration.class);

    // TODO: Decide how to integrate this with ability to serve requests on unix domain socket
    @Bean
    public VerticleDefinition prometheusHttpServerVerticleDefinition(
            PrometheusConfigurationProperties prometheusConfigurationProperties,
            Router prometheusRouter,
            DropwizardExports dropwizardExports) {

        CollectorRegistry.defaultRegistry.register(dropwizardExports);

        return VerticleDefinition.ofSingleInstance(
                () -> new ServerVerticle(
                        "Prometheus Http Server",
                        SocketAddress.inetSocketAddress(prometheusConfigurationProperties.getPort(), "0.0.0.0"),
                        prometheusRouter));
    }

    @Bean
    public SampleBuilder sampleBuilder(PrometheusConfigurationProperties prometheusConfigurationProperties,
                                       List<MapperConfig> mapperConfigs) {

        return new NamespaceSubsystemSampleBuilder(
                prometheusConfigurationProperties.getNamespace(),
                prometheusConfigurationProperties.getSubsystem(),
                mapperConfigs);
    }

    @Bean
    DropwizardExports dropwizardExports(Metrics metrics, MetricRegistry metricRegistry, SampleBuilder sampleBuilder) {
        if (metrics.getCounterType() == CounterType.flushingCounter) {
            logger.warn("Prometheus metric system: Metric type is flushingCounter.");
        }

        return new DropwizardExports(metricRegistry, sampleBuilder);
    }

    @Bean
    Router prometheusRouter(Vertx vertx) {
        final Router router = Router.router(vertx);
        router.route("/metrics").handler(new MetricsHandler());
        return router;
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
