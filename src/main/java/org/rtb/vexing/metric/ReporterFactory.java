package org.rtb.vexing.metric;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;
import com.izettle.metrics.influxdb.InfluxDbHttpSender;
import com.izettle.metrics.influxdb.InfluxDbReporter;
import com.izettle.metrics.influxdb.InfluxDbSender;
import org.apache.commons.lang3.StringUtils;
import org.rtb.vexing.spring.config.MetricsProperties;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class ReporterFactory {

    private ReporterFactory() {
    }

    public static Optional<ScheduledReporter> create(MetricRegistry metricRegistry, MetricsProperties properties) {
        Objects.requireNonNull(metricRegistry);
        Objects.requireNonNull(properties);

        final String reporterType = StringUtils.isNotBlank(properties.getType()) ? properties.getType()
                : StringUtils.EMPTY;

        if (StringUtils.isNotBlank(reporterType)) {
            // format is "<host>:<port>"
            final String hostAndPort = Objects.requireNonNull(properties.getHost(), message("host"));
            final String host = StringUtils.substringBefore(hostAndPort, ":");
            final int port = Integer.parseInt(StringUtils.substringAfter(hostAndPort, ":"));
            final ScheduledReporter reporter;
            switch (ReporterType.valueOf(reporterType)) {
                case graphite:
                    final Graphite graphite = new Graphite(host, port);
                    reporter = GraphiteReporter.forRegistry(metricRegistry)
                            .prefixedWith(Objects.requireNonNull(properties.getPrefix(), message("prefix")))
                            .build(graphite);
                    break;
                case influxdb:
                    final InfluxDbSender influxDbSender;
                    try {
                        influxDbSender = new InfluxDbHttpSender(
                                Objects.requireNonNull(properties.getProtocol(), message("protocol")),
                                host, port,
                                Objects.requireNonNull(properties.getDatabase(), message("database")),
                                Objects.requireNonNull(properties.getAuth(), message("auth")),
                                TimeUnit.SECONDS,
                                Objects.requireNonNull(properties.getConnectTimeout(), message("connectTimeout")),
                                Objects.requireNonNull(properties.getReadTimeout(), message("readTimeout")),
                                Objects.requireNonNull(properties.getPrefix(), message("prefix")));
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Could not initialize influx http sender", e);
                    }
                    reporter = InfluxDbReporter.forRegistry(metricRegistry).build(influxDbSender);
                    break;
                default:
                    throw new IllegalStateException("Should never happen");
            }
            reporter.start(Objects.requireNonNull(properties.getInterval(), message("interval")), TimeUnit.SECONDS);
            return Optional.of(reporter);
        }

        return Optional.empty();
    }

    private enum ReporterType {
        graphite,
        influxdb
    }

    private static String message(String field) {
        return String.format("Configuration property metrics.%s is missing", field);
    }
}
