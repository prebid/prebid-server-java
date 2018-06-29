package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Adapter metrics support.
 */
class AdapterMetrics extends UpdatableMetrics {

    private final RequestTypeMetrics requestTypeMetrics;
    private final RequestMetrics requestMetrics;
    private final Function<String, BidTypeMetrics> bidTypeMetricsCreator;
    private final Map<String, BidTypeMetrics> bidTypeMetrics;

    AdapterMetrics(MetricRegistry metricRegistry, CounterType counterType, String adapterType) {
        super(Objects.requireNonNull(metricRegistry), Objects.requireNonNull(counterType),
                nameCreator(createAdapterPrefix(Objects.requireNonNull(adapterType))));

        bidTypeMetricsCreator = bidType ->
                new BidTypeMetrics(metricRegistry, counterType, createAdapterPrefix(adapterType), bidType);
        requestTypeMetrics = new RequestTypeMetrics(metricRegistry, counterType, createAdapterPrefix(adapterType));
        requestMetrics = new RequestMetrics(metricRegistry, counterType, createAdapterPrefix(adapterType));
        bidTypeMetrics = new HashMap<>();
    }

    AdapterMetrics(MetricRegistry metricRegistry, CounterType counterType, String account, String adapterType) {
        super(Objects.requireNonNull(metricRegistry), Objects.requireNonNull(counterType),
                nameCreator(createAccountAdapterPrefix(Objects.requireNonNull(account),
                        Objects.requireNonNull(adapterType))));

        requestMetrics = new RequestMetrics(metricRegistry, counterType,
                createAccountAdapterPrefix(account, adapterType));

        // not used for account.adapter metrics
        bidTypeMetricsCreator = null;
        requestTypeMetrics = null;
        bidTypeMetrics = null;
    }

    private static String createAdapterPrefix(String adapterType) {
        return String.format("adapter.%s", adapterType);
    }

    private static String createAccountAdapterPrefix(String account, String adapterType) {
        return String.format("account.%s.%s", account, adapterType);
    }

    private static Function<MetricName, String> nameCreator(String prefix) {
        return metricName -> String.format("%s.%s", prefix, metricName.toString());
    }

    RequestTypeMetrics requestType() {
        return requestTypeMetrics;
    }

    RequestMetrics request() {
        return requestMetrics;
    }

    BidTypeMetrics forBidType(String bidType) {
        return bidTypeMetrics.computeIfAbsent(bidType, bidTypeMetricsCreator);
    }
}
