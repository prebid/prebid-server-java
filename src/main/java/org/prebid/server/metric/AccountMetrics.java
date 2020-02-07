package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Account metrics support.
 */
class AccountMetrics extends UpdatableMetrics {

    private final Function<String, AdapterMetrics> adapterMetricsCreator;
    // not thread-safe maps are intentionally used here because it's harmless in this particular case - eventually
    // this all boils down to metrics lookup by underlying metric registry and that operation is guaranteed to be
    // thread-safe
    private final Map<String, AdapterMetrics> adapterMetrics;
    private final RequestTypeMetrics requestTypeMetrics;
    private final RequestMetrics requestsMetrics;

    AccountMetrics(MetricRegistry metricRegistry, CounterType counterType, String account) {
        super(Objects.requireNonNull(metricRegistry), Objects.requireNonNull(counterType),
                nameCreator(createPrefix(Objects.requireNonNull(account))));
        adapterMetricsCreator = adapterType -> new AdapterMetrics(metricRegistry, counterType, account, adapterType);
        adapterMetrics = new HashMap<>();
        requestTypeMetrics = new RequestTypeMetrics(metricRegistry, counterType, createPrefix(account));
        requestsMetrics = new RequestMetrics(metricRegistry, counterType, createPrefix(account));
    }

    private static String createPrefix(String account) {
        return String.format("account.%s", account);
    }

    private static Function<MetricName, String> nameCreator(String prefix) {
        return metricName -> String.format("%s.%s", prefix, metricName.toString());
    }

    AdapterMetrics forAdapter(String adapterType) {
        return adapterMetrics.computeIfAbsent(adapterType, adapterMetricsCreator);
    }

    RequestTypeMetrics requestType() {
        return requestTypeMetrics;
    }

    RequestMetrics requests() {
        return requestsMetrics;
    }
}
