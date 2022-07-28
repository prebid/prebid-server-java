package org.prebid.server.metric;

import org.prebid.server.exception.PreBidException;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Objects;
import java.util.function.Function;

/**
 * Support for TCF metrics.
 */
class TcfMetrics extends UpdatableMetrics {

    private static final int TCF_V1_VERSION = 1;
    private static final int TCF_V2_VERSION = 2;

    private final TcfVersionMetrics tcfVersion1Metrics;
    private final TcfVersionMetrics tcfVersion2Metrics;

    TcfMetrics(MeterRegistry meterRegistry, String prefix) {
        super(Objects.requireNonNull(meterRegistry),
                nameCreator(createTcfPrefix(Objects.requireNonNull(prefix))));

        tcfVersion1Metrics = new TcfVersionMetrics(meterRegistry, createTcfPrefix(prefix), "v1");
        tcfVersion2Metrics = new TcfVersionMetrics(meterRegistry, createTcfPrefix(prefix), "v2");
    }

    TcfVersionMetrics fromVersion(int version) {
        return switch (version) {
            case TCF_V1_VERSION -> tcfVersion1Metrics;
            case TCF_V2_VERSION -> tcfVersion2Metrics;
            default -> throw new PreBidException("Unknown tcf version " + version);
        };
    }

    private static String createTcfPrefix(String prefix) {
        return prefix + ".tcf";
    }

    private static Function<MetricName, String> nameCreator(String prefix) {
        return metricName -> "%s.%s".formatted(prefix, metricName);
    }

    static class TcfVersionMetrics extends UpdatableMetrics {

        private final VendorListMetrics vendorListMetrics;

        TcfVersionMetrics(MeterRegistry meterRegistry, String prefix, String version) {
            super(
                    Objects.requireNonNull(meterRegistry),
                    nameCreator(createVersionPrefix(Objects.requireNonNull(prefix), Objects.requireNonNull(version))));

            vendorListMetrics = new VendorListMetrics(meterRegistry, createVersionPrefix(prefix, version));
        }

        private static String createVersionPrefix(String prefix, String version) {
            return "%s.%s".formatted(prefix, version);
        }

        private static Function<MetricName, String> nameCreator(String prefix) {
            return metricName -> "%s.%s".formatted(prefix, metricName);
        }

        VendorListMetrics vendorList() {
            return vendorListMetrics;
        }
    }

    static class VendorListMetrics extends UpdatableMetrics {

        VendorListMetrics(MeterRegistry meterRegistry, String prefix) {
            super(meterRegistry, nameCreator(createVersionPrefix(prefix)));
        }

        private static String createVersionPrefix(String prefix) {
            return prefix + ".vendorlist";
        }

        private static Function<MetricName, String> nameCreator(String prefix) {
            return metricName -> "%s.%s".formatted(prefix, metricName);
        }
    }
}
