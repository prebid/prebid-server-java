package org.prebid.server.bidder.beachfront.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class BeachfrontVideoDevice {
    String ua;

    @JsonProperty("deviceType")
    Integer deviceType;

    BeachfrontVideoGeo geo;
}
