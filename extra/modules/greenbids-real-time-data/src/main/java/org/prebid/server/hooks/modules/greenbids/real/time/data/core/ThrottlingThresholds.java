package org.prebid.server.hooks.modules.greenbids.real.time.data.core;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class ThrottlingThresholds {

    @JsonProperty("featurizer")
    String featurizer;

    @JsonProperty("pipeline")
    String pipeline;

    @JsonProperty("thresholds")
    List<Double> thresholds;

    @JsonProperty("tpr")
    List<Double> tpr;

    @JsonProperty("fpr")
    List<Double> fpr;

    @JsonProperty("version")
    String version;

    @JsonCreator
    public ThrottlingThresholds(
            @JsonProperty("featurizer") String featurizer,
            @JsonProperty("pipeline") String pipeline,
            @JsonProperty("thresholds") List<Double> thresholds,
            @JsonProperty("tpr") List<Double> tpr,
            @JsonProperty("fpr") List<Double> fpr,
            @JsonProperty("version") String version) {
        this.featurizer = featurizer;
        this.pipeline = pipeline;
        this.thresholds = thresholds;
        this.tpr = tpr;
        this.fpr = fpr;
        this.version = version;
    }
}
