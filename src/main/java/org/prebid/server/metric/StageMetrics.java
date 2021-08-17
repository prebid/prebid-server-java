package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;
import org.prebid.server.hooks.execution.model.Stage;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

class StageMetrics extends UpdatableMetrics {

    private static final EnumMap<Stage, String> STAGE_TO_METRIC = new EnumMap<>(Stage.class);

    static {
        STAGE_TO_METRIC.put(Stage.entrypoint, "entrypoint");
        STAGE_TO_METRIC.put(Stage.raw_auction_request, "rawauction");
        STAGE_TO_METRIC.put(Stage.processed_auction_request, "procauction");
        STAGE_TO_METRIC.put(Stage.bidder_request, "bidrequest");
        STAGE_TO_METRIC.put(Stage.raw_bidder_response, "rawbidresponse");
        STAGE_TO_METRIC.put(Stage.processed_bidder_response, "procbidresponse");
        STAGE_TO_METRIC.put(Stage.auction_response, "auctionresponse");
    }

    private static final String UNKNOWN_STAGE = "unknown";

    // not thread-safe maps are intentionally used here because it's harmless in this particular case - eventually
    // this all boils down to metrics lookup by underlying metric registry and that operation is guaranteed to be
    // thread-safe
    private final Function<String, HookImplMetrics> hookImplMetricsCreator;
    private final Map<String, HookImplMetrics> hookImplMetrics;

    StageMetrics(MetricRegistry metricRegistry, CounterType counterType, String prefix, Stage stage) {
        super(
                Objects.requireNonNull(metricRegistry),
                Objects.requireNonNull(counterType),
                nameCreator(createPrefix(Objects.requireNonNull(prefix), Objects.requireNonNull(stage))));

        hookImplMetricsCreator = hookImplCode ->
                new HookImplMetrics(metricRegistry, counterType, createPrefix(prefix, stage), hookImplCode);
        hookImplMetrics = new HashMap<>();
    }

    private static Function<MetricName, String> nameCreator(String prefix) {
        return metricName -> String.format("%s.%s", prefix, metricName.toString());
    }

    private static String createPrefix(String prefix, Stage stage) {
        return String.format("%s.stage.%s", prefix, STAGE_TO_METRIC.getOrDefault(stage, UNKNOWN_STAGE));
    }

    HookImplMetrics hookImpl(String hookImpl) {
        return hookImplMetrics.computeIfAbsent(hookImpl, hookImplMetricsCreator);
    }
}
