package org.prebid.server.hooks.modules.greenbids.real.time.data.core;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class ThrottlingThresholds {

    @JsonProperty("thresholds")
    List<Double> thresholds;

    @JsonProperty("tpr")
    List<Double> tpr;

    @JsonProperty("fpr")
    List<Double> fpr;

    @JsonProperty("version")
    String version;
}
