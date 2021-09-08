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

    // not thread-safe maps are intentionally used here because it's harmless in this particular case - eventually
    // this all boils down to metrics lookup by underlying metric registry and that operation is guaranteed to be
    // thread-safe
    private final Function<MetricName, RequestTypeMetrics> requestTypeMetricsCreator;
    private final Map<MetricName, RequestTypeMetrics> requestTypeMetrics;
    private final AdapterMetrics adapterMetrics;
    private final RequestMetrics requestsMetrics;
    private final CacheMetrics cacheMetrics;
    private final ResponseMetrics responseMetrics;
    private final HooksMetrics hooksMetrics;

    AccountMetrics(MetricRegistry metricRegistry, CounterType counterType, String account) {
        super(Objects.requireNonNull(metricRegistry), Objects.requireNonNull(counterType),
                nameCreator(createPrefix(Objects.requireNonNull(account))));
        requestTypeMetricsCreator = requestType ->
                new RequestTypeMetrics(metricRegistry, counterType, createPrefix(account), requestType);
        adapterMetrics = new AdapterMetrics(metricRegistry, counterType, createPrefix(account));
        requestTypeMetrics = new HashMap<>();
        requestsMetrics = new RequestMetrics(metricRegistry, counterType, createPrefix(account));
        cacheMetrics = new CacheMetrics(metricRegistry, counterType, createPrefix(account));
        responseMetrics = new ResponseMetrics(metricRegistry, counterType, createPrefix(account));
        hooksMetrics = new HooksMetrics(metricRegistry, counterType, createPrefix(account));
    }

    private static String createPrefix(String account) {
        return String.format("account.%s", account);
    }

    private static Function<MetricName, String> nameCreator(String prefix) {
        return metricName -> String.format("%s.%s", prefix, metricName.toString());
    }

    AdapterMetrics adapter() {
        return adapterMetrics;
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

    HooksMetrics hooks() {
        return hooksMetrics;
    }
}
