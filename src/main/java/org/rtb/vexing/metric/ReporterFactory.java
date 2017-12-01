package org.rtb.vexing.metric;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;
import com.izettle.metrics.influxdb.InfluxDbHttpSender;
import com.izettle.metrics.influxdb.InfluxDbReporter;
import com.izettle.metrics.influxdb.InfluxDbSender;
import org.apache.commons.lang3.StringUtils;
import org.rtb.vexing.config.ApplicationConfig;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class ReporterFactory {

    private ReporterFactory() {
    }

    public static Optional<ScheduledReporter> create(MetricRegistry metricRegistry, ApplicationConfig config) {
        Objects.requireNonNull(metricRegistry);
        Objects.requireNonNull(config);

        final String reporterType = config.getString("metrics.type", StringUtils.EMPTY);

        if (StringUtils.isNotBlank(reporterType)) {
            // format is "<host>:<port>"
            final String hostAndPort = config.getString("metrics.host");
            final String host = StringUtils.substringBefore(hostAndPort, ":");
            final int port = Integer.parseInt(StringUtils.substringAfter(hostAndPort, ":"));
            final ScheduledReporter reporter;
            switch (ReporterType.valueOf(reporterType)) {
                case graphite:
                    final Graphite graphite = new Graphite(host, port);
                    reporter = GraphiteReporter.forRegistry(metricRegistry)
                            .prefixedWith(config.getString("metrics.prefix"))
                            .build(graphite);
                    break;
                case influxdb:
                    final InfluxDbSender influxDbSender;
                    try {
                        influxDbSender = new InfluxDbHttpSender(config.getString("metrics.protocol"),
                                host, port,
                                config.getString("metrics.database"),
                                config.getString("metrics.auth"),
                                TimeUnit.SECONDS,
                                config.getInteger("metrics.connectTimeout"),
                                config.getInteger("metrics.readTimeout"),
                                config.getString("metrics.prefix"));
                    } catch (Exception e) {
                        throw new IllegalArgumentException("Could not initialize influx http sender", e);
                    }
                    reporter = InfluxDbReporter.forRegistry(metricRegistry).build(influxDbSender);
                    break;
                default:
                    throw new IllegalStateException("Should never happen");
            }
            reporter.start(config.getInteger("metrics.interval"), TimeUnit.SECONDS);
            return Optional.of(reporter);
        }

        return Optional.empty();
    }

    private enum ReporterType {
        graphite,
        influxdb
    }
}
