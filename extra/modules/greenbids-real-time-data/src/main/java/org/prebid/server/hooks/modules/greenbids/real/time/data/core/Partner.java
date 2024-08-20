package org.prebid.server.hooks.modules.greenbids.real.time.data.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.Comparator;
import java.util.List;

@Builder(toBuilder = true)
@Value
public class Partner {

    @JsonProperty("pbuid")
    String pbuid;

    @JsonProperty("targetTpr")
    Double targetTpr;

    @JsonProperty("explorationRate")
    Double explorationRate;

    @JsonCreator
    public Partner(
            @JsonProperty("pbuid") String pbuid,
            @JsonProperty("targetTpr") Double targetTpr,
            @JsonProperty("explorationRate") Double explorationRate) {
        this.pbuid = pbuid;
        this.targetTpr = targetTpr;
        this.explorationRate = explorationRate;
    }

    public Double getThresholdForPartner(ThrottlingThresholds throttlingThresholds) {
        final List<Double> truePositiveRates = throttlingThresholds.getTpr();
        final List<Double> thresholds = throttlingThresholds.getThresholds();

        return truePositiveRates.stream()
                .filter(truePositiveRate -> truePositiveRate >= targetTpr)
                .map(truePositiveRate -> thresholds.get(truePositiveRates.indexOf(truePositiveRate)))
                .max(Comparator.naturalOrder())
                .orElse(0.0);
    }
}
