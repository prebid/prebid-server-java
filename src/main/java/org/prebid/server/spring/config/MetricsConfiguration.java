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
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.metric.CounterType;
import org.prebid.server.metric.Metrics;
import org.prebid.server.vertx.CloseableAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
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

    @Bean
    @ConditionalOnProperty(prefix = "metrics", name = "type", havingValue = "graphite")
    ScheduledReporter graphiteReporter(GraphiteProperties graphiteProperties, MetricRegistry metricRegistry) {
        // format is "<host>:<port>"
        final String hostAndPort = graphiteProperties.getHost();

        final Graphite graphite = new Graphite(host(hostAndPort), port(hostAndPort));
        final ScheduledReporter reporter = GraphiteReporter.forRegistry(metricRegistry)
                .prefixedWith(graphiteProperties.getPrefix())
                .build(graphite);
        reporter.start(graphiteProperties.getInterval(), TimeUnit.SECONDS);

        return reporter;
    }

    @Bean
    @ConditionalOnProperty(prefix = "metrics", name = "type", havingValue = "influxdb")
    ScheduledReporter influxdbReporter(InfluxdbProperties influxdbProperties, MetricRegistry metricRegistry)
            throws Exception {
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
        final ScheduledReporter reporter = InfluxDbReporter.forRegistry(metricRegistry).build(influxDbSender);
        reporter.start(influxdbProperties.getInterval(), TimeUnit.SECONDS);

        return reporter;
    }

    @Bean
    Metrics metrics(@Value("${metrics.metricType}") CounterType counterType, MetricRegistry metricRegistry,
                    BidderCatalog bidderCatalog) {
        return new Metrics(metricRegistry, counterType, bidderCatalog);
    }

    @Bean
    MetricRegistry metricRegistry() {
        return new MetricRegistry();
    }

    @PostConstruct
    void registerReporterCloseHooks() {
        reporters.stream()
                .map(CloseableAdapter::new)
                .forEach(closeable -> vertx.getOrCreateContext().addCloseHook(closeable));
    }

    @Component
    @ConfigurationProperties(prefix = "metrics")
    @ConditionalOnProperty(prefix = "metrics", name = "type", havingValue = "graphite")
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
    @ConditionalOnProperty(prefix = "metrics", name = "type", havingValue = "influxdb")
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

    private static String host(String hostAndPort) {
        return StringUtils.substringBefore(hostAndPort, ":");
    }

    private static int port(String hostAndPort) {
        return Integer.parseInt(StringUtils.substringAfter(hostAndPort, ":"));
    }
}
