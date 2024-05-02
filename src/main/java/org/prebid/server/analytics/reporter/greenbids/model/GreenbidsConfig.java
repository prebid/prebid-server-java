package org.prebid.server.analytics.reporter.greenbids.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class GreenbidsConfig {

    @JsonProperty("pbuid")
    String pbuid;

    @JsonProperty("greenbidsSampling")
    Double greenbidsSampling;
}
