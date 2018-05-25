package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Registry of metrics for an account metrics support.
 */
public class AdapterMetrics extends UpdatableMetrics {

    private final Function<BidType, MarkupMetrics> markupMetricsCreator;
    private final Map<BidType, MarkupMetrics> markupMetrics;

    AdapterMetrics(MetricRegistry metricRegistry, CounterType counterType, String adapterType) {
        super(Objects.requireNonNull(metricRegistry), Objects.requireNonNull(counterType),
                nameCreator(String.format("adapter.%s", Objects.requireNonNull(adapterType))));

        markupMetricsCreator = bidType -> new MarkupMetrics(metricRegistry, counterType,
                String.format("adapter.%s.%s", Objects.requireNonNull(adapterType), bidType.name()));
        markupMetrics = new HashMap<>();
    }

    AdapterMetrics(MetricRegistry metricRegistry, CounterType counterType, String account, String adapterType) {
        super(Objects.requireNonNull(metricRegistry), Objects.requireNonNull(counterType),
                nameCreator(String.format("account.%s.%s", Objects.requireNonNull(account),
                        Objects.requireNonNull(adapterType))));

        // not used for account.adapter metrics
        markupMetricsCreator = null;
        markupMetrics = null;
    }

    private static Function<MetricName, String> nameCreator(String prefix) {
        return metricName -> String.format("%s.%s", prefix, metricName.name());
    }

    public MarkupMetrics forBidType(BidType bidType) {
        return markupMetrics.computeIfAbsent(bidType, markupMetricsCreator);
    }

    /**
     * Markup delivery metrics for reporting on certain bid type
     */
    public static class MarkupMetrics extends UpdatableMetrics {

        MarkupMetrics(MetricRegistry metricRegistry, CounterType counterType, String prefix) {
            super(metricRegistry, counterType, nameCreator(prefix));
        }

        private static Function<MetricName, String> nameCreator(String prefix) {
            return metricName -> String.format("%s.%s", prefix, metricName.name());
        }
    }
}
