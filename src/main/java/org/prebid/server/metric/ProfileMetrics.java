package org.prebid.server.metric;

import com.codahale.metrics.MetricRegistry;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;
import java.util.function.Function;

class ProfileMetrics extends UpdatableMetrics {

    ProfileMetrics(MetricRegistry metricRegistry, CounterType counterType) {
        this(metricRegistry, counterType, StringUtils.EMPTY);
    }

    ProfileMetrics(MetricRegistry metricRegistry, CounterType counterType, String prefix) {
        super(
                Objects.requireNonNull(metricRegistry),
                Objects.requireNonNull(counterType),
                nameCreator(Objects.requireNonNull(prefix) + "."));
    }

    private static Function<MetricName, String> nameCreator(String prefix) {
        return metricName -> "%sprofile.%s".formatted(prefix, metricName);
    }
}
