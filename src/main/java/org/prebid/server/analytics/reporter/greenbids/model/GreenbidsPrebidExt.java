package org.prebid.server.analytics.reporter.greenbids.model;

import lombok.Value;

@Value(staticConstructor = "of")
public class GreenbidsPrebidExt {

    String pbuid;

    Double greenbidsSampling;
}
