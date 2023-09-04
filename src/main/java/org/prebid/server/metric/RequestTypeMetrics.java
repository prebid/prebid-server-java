package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.Objects;
import java.util.function.Function;

/**
 * Request metrics support.
 */
class RequestTypeMetrics extends UpdatableMetrics {

    private final TcfMetrics tcfMetrics;

    RequestTypeMetrics(MeterRegistry meterRegistry, CounterType counterType, String prefix, MetricName requestType) {
        super(Objects.requireNonNull(meterRegistry), Objects.requireNonNull(counterType),
                nameCreator(Objects.requireNonNull(prefix), Objects.requireNonNull(requestType)));
        tcfMetrics = new TcfMetrics(meterRegistry, counterType, createTcfPrefix(prefix, requestType));
    }

    TcfMetrics tcf() {
        return tcfMetrics;
    }

    private static Function<MetricName, String> nameCreator(String prefix, MetricName requestType) {
        return metricName -> "%s.%s.type.%s".formatted(prefix, metricName, requestType);
    }

    private static String createTcfPrefix(String prefix, MetricName requestType) {
        return "%s.%s".formatted(prefix, requestType);
    }
}
