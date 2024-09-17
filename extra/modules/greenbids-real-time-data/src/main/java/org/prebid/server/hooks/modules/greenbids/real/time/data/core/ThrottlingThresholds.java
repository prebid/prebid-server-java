package org.prebid.server.hooks.modules.greenbids.real.time.data.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
@JsonIgnoreProperties(ignoreUnknown = true)
public class ThrottlingThresholds {

    List<Double> thresholds;

    List<Double> tpr;
}
