package org.prebid.server.handler;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.util.HttpUtil;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CollectedMetricsHandler implements Handler<RoutingContext> {

    private final MetricRegistry metricRegistry;
    private final JacksonMapper mapper;
    private final String endpoint;

    public CollectedMetricsHandler(MetricRegistry metricRegistry,
                                   JacksonMapper mapper,
                                   String endpoint) {
        this.metricRegistry = Objects.requireNonNull(metricRegistry);
        this.mapper = Objects.requireNonNull(mapper);
        this.endpoint = Objects.requireNonNull(endpoint);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final String responseString = mapper.encode(getAllMetrics());

        HttpUtil.executeSafely(routingContext, endpoint,
                response -> response
                        .putHeader(HttpUtil.CONTENT_TYPE_HEADER, HttpUtil.APPLICATION_JSON_CONTENT_TYPE)
                        .end(responseString));
    }

    private Map<String, Number> getAllMetrics() {
        return Stream.of(getMetric(metricRegistry.getGauges(), Gauge::getValue),
                        getMetric(metricRegistry.getCounters(), Counter::getCount),
                        getMetric(metricRegistry.getHistograms(), metric -> metric.getSnapshot().get95thPercentile()),
                        getMetric(metricRegistry.getMeters(), Meter::getCount),
                        getMetric(metricRegistry.getTimers(), Timer::getCount))
                .flatMap(Function.identity())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (o1, o2) -> o1, TreeMap::new));
    }

    private static <T extends Metric> Stream<Map.Entry<String, Number>> getMetric(Map<String, T> metricMap,
                                                                                  Function<T, ?> getter) {
        return metricMap.entrySet().stream()
                .map(entry -> new AbstractMap.SimpleEntry<>(entry.getKey(), (Number) getter.apply(entry.getValue())));
    }
}
