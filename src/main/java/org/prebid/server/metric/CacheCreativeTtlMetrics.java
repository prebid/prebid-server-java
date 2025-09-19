package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;
import org.prebid.server.metric.model.CacheCreativeType;

import java.util.Objects;
import java.util.function.Function;

public class CacheCreativeTtlMetrics extends UpdatableMetrics {

    CacheCreativeTtlMetrics(MetricRegistry metricRegistry,
                            CounterType counterType,
                            String prefix,
                            CacheCreativeType type) {

        super(Objects.requireNonNull(metricRegistry),
                Objects.requireNonNull(counterType),
                nameCreator(Objects.requireNonNull(prefix), Objects.requireNonNull(type)));
    }

    private static Function<MetricName, String> nameCreator(String prefix, CacheCreativeType type) {
        return metricName -> "%s.%s_ttl.%s".formatted(prefix, type.getType(), metricName);
    }
}
