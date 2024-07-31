package org.prebid.server.hooks.modules.greenbids.real.time.data.core;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder(toBuilder = true)
@Value
public class ThrottlingThresholds {

    String featurizer;

    List<RocCurve> rocCurves;

    String version;
}
