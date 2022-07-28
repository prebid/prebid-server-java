package org.prebid.server.metric;

import io.micrometer.core.instrument.MeterRegistry;

import java.util.Objects;
import java.util.function.Function;

public class PriceFloorMetrics extends UpdatableMetrics {

    PriceFloorMetrics(MeterRegistry meterRegistry, String prefix) {
        super(Objects.requireNonNull(meterRegistry),
                nameCreator(Objects.requireNonNull(prefix)));
    }

    private static Function<MetricName, String> nameCreator(String prefix) {
        return metricName -> "price-floors.%s.%s".formatted(prefix, metricName);
    }
}
