package org.prebid.server.hooks.modules.greenbids.real.time.data.model.filter;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class ThrottlingThresholds {

    List<Double> thresholds;

    List<Double> tpr;
}
