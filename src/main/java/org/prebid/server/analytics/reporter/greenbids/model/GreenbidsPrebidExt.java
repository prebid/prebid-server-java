package org.prebid.server.analytics.reporter.greenbids.model;

import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class GreenbidsPrebidExt {

    String pbuid;

    Double greenbidsSampling;
}
