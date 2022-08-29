package org.prebid.server.handler;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.Timer;
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

public class CollectedMetricsHandler implements Handler<RoutingContext> {

    private final MeterRegistry meterRegistry;
    private final JacksonMapper mapper;
    private final String endpoint;

    public CollectedMetricsHandler(MeterRegistry meterRegistry,
                                   JacksonMapper mapper,
                                   String endpoint) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry);
        this.mapper = Objects.requireNonNull(mapper);
        this.endpoint = Objects.requireNonNull(endpoint);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final String responseString = mapper.encodeToString(getAllMetrics());

        HttpUtil.executeSafely(routingContext, endpoint,
                response -> response
                        .putHeader(HttpUtil.CONTENT_TYPE_HEADER, HttpUtil.APPLICATION_JSON_CONTENT_TYPE)
                        .end(responseString));
    }

    private Map<String, Number> getAllMetrics() {
        Function<Gauge, Number> getGaugeValue = m -> m.value();
        Function<Counter, Number> getCounterValue = m -> m.count();
        Function<Timer, Number> getTimerValue = m -> m.count();
        Function<DistributionSummary, Number> getSummaryValue = m -> m.takeSnapshot().count();
        Function<LongTaskTimer, Number> getLongTaskTimerValue = m -> m.takeSnapshot().count();
        Function<TimeGauge, Number> getTimeGaugeValue = m -> m.value();
        Function<FunctionCounter, Number> getFunctionCounterValue = m -> m.count();
        Function<FunctionTimer, Number> getFunctionTimerValue = m -> m.count();
        Function<Meter, Number> getMeterValue = m -> 0;

        return meterRegistry.getMeters()
                    .stream()
                    .map(m -> new AbstractMap.SimpleEntry<>(
                        String.format(
                            "%s{%s}",
                            m.getId().getName(),
                            m.getId().getTags()
                                .stream()
                                .map(t -> String.format("%s=%s", t.getKey(), t.getValue()))
                                .collect(Collectors.joining(","))
                        ),
                        (Number) m.match(
                            getGaugeValue,
                            getCounterValue,
                            getTimerValue,
                            getSummaryValue,
                            getLongTaskTimerValue,
                            getTimeGaugeValue,
                            getFunctionCounterValue,
                            getFunctionTimerValue,
                            getMeterValue
                            )))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (o1, o2) -> o1, TreeMap::new));
    }
}
