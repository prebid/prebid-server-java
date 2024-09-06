package org.prebid.server.hooks.modules.greenbids.real.time.data.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
@JsonIgnoreProperties(ignoreUnknown = true)
public class ThrottlingThresholds {

    @JsonProperty("thresholds")
    List<Double> thresholds;

    @JsonProperty("tpr")
    List<Double> tpr;
}
