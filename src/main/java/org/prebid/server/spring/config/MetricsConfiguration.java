package org.prebid.server.spring.config;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;
import com.izettle.metrics.influxdb.InfluxDbHttpSender;
import com.izettle.metrics.influxdb.InfluxDbReporter;
import com.izettle.metrics.influxdb.InfluxDbSender;
import io.vertx.core.Vertx;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.metric.CounterType;
import org.prebid.server.metric.Metrics;
import org.prebid.server.metric.noop.NoOpMetrics;
import org.prebid.server.vertx.CloseableAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.AllNestedConditions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.annotation.PostConstruct;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
public class MetricsConfiguration {

    @Autowired(required = false)
    private List<ScheduledReporter> reporters = Collections.emptyList();
    @Autowired
    private Vertx vertx;

    @PostConstruct
    void registerReporterCloseHooks() {
        reporters.stream()
                .map(CloseableAdapter::new)
                .forEach(closeable -> vertx.getOrCreateContext().addCloseHook(closeable));
    }

    @Bean(name = "dropwizardMetrics")
    @Conditional({EnableDropwizardMetrics.class})
    Metrics metrics(@Value("${metrics.metricType}") CounterType counterType) {
        return new org.prebid.server.metric.dropwizard.Metrics(new MetricRegistry(), counterType);
    }

    @Bean
    @ConditionalOnProperty(prefix = "metrics", name = "kind", havingValue = "noop")
    Metrics metrics() {
        return new NoOpMetrics();
    }

    @Component
    @ConfigurationProperties(prefix = "metrics")
    @Conditional({EnableGraphiteReporter.class})
    @Validated
    @Data
    @NoArgsConstructor
    private static class GraphiteProperties {

        @NotBlank
        private String host;
        @NotBlank
        private String prefix;
        @NotNull
        @Min(1)
        private Integer interval;
    }

    @Component
    @ConfigurationProperties(prefix = "metrics")
    @Conditional({EnableInfluxdbReporter.class})
    @Validated
    @Data
    @NoArgsConstructor
    private static class InfluxdbProperties {

        @NotBlank
        private String host;
        @NotBlank
        private String prefix;
        @NotBlank
        private String protocol;
        @NotBlank
        private String database;
        @NotBlank
        private String auth;
        @NotNull
        @Min(1)
        private Integer connectTimeout;
        @NotNull
        @Min(1)
        private Integer readTimeout;
        @NotNull
        @Min(1)
        private Integer interval;
    }

    @Bean
    @Conditional({EnableGraphiteReporter.class})
    ScheduledReporter graphiteReporter(GraphiteProperties graphiteProperties,
                                       @Qualifier("dropwizardMetrics") Metrics metrics) {
        // format is "<host>:<port>"
        final String hostAndPort = graphiteProperties.getHost();

        final Graphite graphite = new Graphite(host(hostAndPort), port(hostAndPort));
        final ScheduledReporter reporter = GraphiteReporter.forRegistry(
                ((org.prebid.server.metric.dropwizard.Metrics) metrics).getMetricRegistry())
                .prefixedWith(graphiteProperties.getPrefix())
                .build(graphite);
        reporter.start(graphiteProperties.getInterval(), TimeUnit.SECONDS);

        return reporter;
    }

    @Bean
    @Conditional({EnableInfluxdbReporter.class})
    ScheduledReporter influxdbReporter(InfluxdbProperties influxdbProperties,
                                       @Qualifier("dropwizardMetrics") Metrics metrics) throws Exception {
        // format is "<host>:<port>"
        final String hostAndPort = influxdbProperties.getHost();

        final InfluxDbSender influxDbSender = new InfluxDbHttpSender(
                influxdbProperties.getProtocol(),
                host(hostAndPort), port(hostAndPort),
                influxdbProperties.getDatabase(),
                influxdbProperties.getAuth(),
                TimeUnit.SECONDS,
                influxdbProperties.getConnectTimeout(),
                influxdbProperties.getReadTimeout(),
                influxdbProperties.getPrefix());
        final ScheduledReporter reporter = InfluxDbReporter.forRegistry(
                ((org.prebid.server.metric.dropwizard.Metrics) metrics).getMetricRegistry()).build(influxDbSender);
        reporter.start(influxdbProperties.getInterval(), TimeUnit.SECONDS);

        return reporter;
    }

    private static String host(String hostAndPort) {
        return StringUtils.substringBefore(hostAndPort, ":");
    }

    private static int port(String hostAndPort) {
        return Integer.parseInt(StringUtils.substringAfter(hostAndPort, ":"));
    }

    static class EnableDropwizardMetrics extends AllNestedConditions {

        EnableDropwizardMetrics() {
            super(ConfigurationPhase.REGISTER_BEAN);
        }

        @ConditionalOnProperty(prefix = "metrics", name = "kind", havingValue = "dropwizard", matchIfMissing = true)
        static class DropwizardMetrics { }
    }

    static class EnableGraphiteReporter extends EnableDropwizardMetrics {

        @ConditionalOnProperty(prefix = "metrics", name = "type", havingValue = "graphite")
        static class GraphiteReporter { }

    }

    static class EnableInfluxdbReporter extends EnableDropwizardMetrics {

        @ConditionalOnProperty(prefix = "metrics", name = "type", havingValue = "influxdb")
        static class GraphiteReporter { }

    }
}
