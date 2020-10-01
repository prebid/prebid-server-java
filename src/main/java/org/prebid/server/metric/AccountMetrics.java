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
    private final Function<MetricName, RequestTypeMetrics> requestTypeMetricsCreator;
    private final Map<MetricName, RequestTypeMetrics> requestTypeMetrics;
    private final RequestMetrics requestsMetrics;
    private final CacheMetrics cacheMetrics;
    private final ResponseMetrics responseMetrics;

    AccountMetrics(MetricRegistry metricRegistry, CounterType counterType, String account) {
        super(Objects.requireNonNull(metricRegistry), Objects.requireNonNull(counterType),
                nameCreator(createPrefix(Objects.requireNonNull(account))));
        adapterMetricsCreator = adapterType -> new AdapterMetrics(metricRegistry, counterType, account, adapterType);
        adapterMetrics = new HashMap<>();
        requestTypeMetricsCreator = requestType ->
                new RequestTypeMetrics(metricRegistry, counterType, createPrefix(account), requestType);
        requestTypeMetrics = new HashMap<>();
        requestsMetrics = new RequestMetrics(metricRegistry, counterType, createPrefix(account));
        cacheMetrics = new CacheMetrics(metricRegistry, counterType, createPrefix(account));
        responseMetrics = new ResponseMetrics(metricRegistry, counterType, createPrefix(account));
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

    RequestTypeMetrics requestType(MetricName requestType) {
        return requestTypeMetrics.computeIfAbsent(requestType, requestTypeMetricsCreator);
    }

    RequestMetrics requests() {
        return requestsMetrics;
    }

    CacheMetrics cache() {
        return cacheMetrics;
    }

    ResponseMetrics response() {
        return responseMetrics;
    }
}
