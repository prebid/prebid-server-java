package org.prebid.server.hooks.modules.greenbids.real.time.data.model.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.filter.ThrottlingThresholds;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

@Value(staticConstructor = "of")
public class Partner {

    @JsonProperty(required = true)
    Boolean enabled;

    String pbuid;

    @JsonProperty("targetTpr")
    Double targetTpr;

    @JsonProperty("explorationRate")
    Double explorationRate;

    public Double getThreshold(ThrottlingThresholds throttlingThresholds) {
        final List<Double> truePositiveRates = throttlingThresholds.getTpr();
        final List<Double> thresholds = throttlingThresholds.getThresholds();

        final int minSize = Math.min(truePositiveRates.size(), thresholds.size());

        return IntStream.range(0, minSize)
                .filter(i -> truePositiveRates.get(i) >= targetTpr)
                .mapToObj(thresholds::get)
                .max(Comparator.naturalOrder())
                .orElse(0.0);
    }
}
