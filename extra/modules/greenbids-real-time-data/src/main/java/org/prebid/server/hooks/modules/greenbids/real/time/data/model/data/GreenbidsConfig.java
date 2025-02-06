package org.prebid.server.hooks.modules.greenbids.real.time.data.model.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.filter.ThrottlingThresholds;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

@Value(staticConstructor = "of")
public class GreenbidsConfig {

    private static final double DEFAULT_TPR = 1.0;

    String pbuid;

    @JsonProperty("target-tpr")
    Double targetTpr;

    @JsonProperty("exploration-rate")
    Double explorationRate;

    public Double getThreshold(ThrottlingThresholds throttlingThresholds) {
        final double safeTargetTpr = targetTpr != null ? targetTpr : DEFAULT_TPR;
        final List<Double> truePositiveRates = throttlingThresholds.getTpr();
        final List<Double> thresholds = throttlingThresholds.getThresholds();

        final int minSize = Math.min(truePositiveRates.size(), thresholds.size());

        return IntStream.range(0, minSize)
                .filter(i -> truePositiveRates.get(i) >= safeTargetTpr)
                .mapToObj(thresholds::get)
                .max(Comparator.naturalOrder())
                .orElse(0.0);
    }
}
