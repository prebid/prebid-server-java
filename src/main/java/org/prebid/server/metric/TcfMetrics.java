package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Support for TCF metrics.
 */
class TcfMetrics extends UpdatableMetrics {

    private final TcfVersionMetrics tcfVersion1Metrics;
    private final TcfVersionMetrics tcfVersion2Metrics;

    TcfMetrics(MetricRegistry metricRegistry, CounterType counterType, String prefix) {
        super(
                Objects.requireNonNull(metricRegistry),
                Objects.requireNonNull(counterType),
                nameCreator(createTcfPrefix(Objects.requireNonNull(prefix))));

        tcfVersion1Metrics = new TcfVersionMetrics(metricRegistry, counterType, createTcfPrefix(prefix), "v1");
        tcfVersion2Metrics = new TcfVersionMetrics(metricRegistry, counterType, createTcfPrefix(prefix), "v2");
    }

    TcfVersionMetrics v1() {
        return tcfVersion1Metrics;
    }

    TcfVersionMetrics v2() {
        return tcfVersion2Metrics;
    }

    private static String createTcfPrefix(String prefix) {
        return String.format("%s.tcf", prefix);
    }

    private static Function<MetricName, String> nameCreator(String prefix) {
        return metricName -> String.format("%s.%s", prefix, metricName.toString());
    }

    static class TcfVersionMetrics extends UpdatableMetrics {

        private final Function<Integer, VendorListMetrics> vendorListMetricsCreator;
        private final Map<Integer, VendorListMetrics> vendorListMetrics;

        TcfVersionMetrics(MetricRegistry metricRegistry, CounterType counterType, String prefix, String version) {
            super(
                    Objects.requireNonNull(metricRegistry),
                    Objects.requireNonNull(counterType),
                    nameCreator(createVersionPrefix(Objects.requireNonNull(prefix), Objects.requireNonNull(version))));

            vendorListMetricsCreator = vendorList -> new VendorListMetrics(metricRegistry, counterType,
                    createVersionPrefix(prefix, version), vendorList);
            vendorListMetrics = new HashMap<>();
        }

        private static String createVersionPrefix(String prefix, String version) {
            return String.format("%s.%s", prefix, version);
        }

        private static Function<MetricName, String> nameCreator(String prefix) {
            return metricName -> String.format("%s.%s", prefix, metricName.toString());
        }

        public VendorListMetrics forVendorList(int vendorList) {
            return vendorListMetrics.computeIfAbsent(vendorList, vendorListMetricsCreator);
        }
    }

    static class VendorListMetrics extends UpdatableMetrics {

        VendorListMetrics(MetricRegistry metricRegistry, CounterType counterType, String prefix, int vendorList) {
            super(
                    metricRegistry,
                    counterType,
                    nameCreator(createVersionPrefix(prefix, vendorList)));
        }

        private static String createVersionPrefix(String prefix, int vendorList) {
            return String.format("%s.vendorlist.%s", prefix, vendorList);
        }

        private static Function<MetricName, String> nameCreator(String prefix) {
            return metricName -> String.format("%s.%s", prefix, metricName.toString());
        }
    }
}
