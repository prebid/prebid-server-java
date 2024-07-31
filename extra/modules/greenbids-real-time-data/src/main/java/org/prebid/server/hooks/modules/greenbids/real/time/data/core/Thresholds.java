package org.prebid.server.hooks.modules.greenbids.real.time.data.core;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class Thresholds {

    List<Double> thresholds;

    List<Double> truePositiveRates;

    List<Double> trueNegativeRates;
}
