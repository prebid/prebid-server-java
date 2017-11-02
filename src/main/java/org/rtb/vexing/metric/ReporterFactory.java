package org.rtb.vexing.metric;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.ScheduledReporter;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;
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

            switch (ReporterType.valueOf(reporterType)) {
                case graphite:
                    final Graphite graphite = new Graphite(host, port);
                    final GraphiteReporter reporter = GraphiteReporter.forRegistry(metricRegistry)
                            .prefixedWith(config.getString("metrics.prefix"))
                            .build(graphite);
                    reporter.start(config.getInteger("metrics.interval"), TimeUnit.SECONDS);

                    return Optional.of(reporter);
                default:
                    throw new IllegalStateException("Should never happen");
            }
        }

        return Optional.empty();
    }

    private enum ReporterType {
        graphite
    }
}
