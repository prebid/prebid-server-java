package org.prebid.server.analytics.reporter.greenbids.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class GreenbidsPrebidExt {

    @JsonProperty("pbuid")
    String pbuid;

    @JsonProperty("greenbidsSampling")
    Double greenbidsSampling;

}
