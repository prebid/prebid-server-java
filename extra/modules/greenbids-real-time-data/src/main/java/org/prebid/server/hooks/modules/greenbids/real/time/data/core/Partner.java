package org.prebid.server.hooks.modules.greenbids.real.time.data.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.Comparator;
import java.util.List;

@Value(staticConstructor = "of")
public class Partner {

    String pbuid;

    @JsonProperty("targetTpr")
    Double targetTpr;

    @JsonProperty("explorationRate")
    Double explorationRate;

    public Double getThreshold(ThrottlingThresholds throttlingThresholds) {
        final List<Double> truePositiveRates = throttlingThresholds.getTpr();
        final List<Double> thresholds = throttlingThresholds.getThresholds();

        return truePositiveRates.stream()
                .filter(truePositiveRate -> truePositiveRate >= targetTpr)
                .map(truePositiveRate -> thresholds.get(truePositiveRates.indexOf(truePositiveRate)))
                .max(Comparator.naturalOrder())
                .orElse(0.0);
    }
}
