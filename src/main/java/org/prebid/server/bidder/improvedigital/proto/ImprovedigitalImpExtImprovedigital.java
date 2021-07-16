package org.prebid.server.bidder.improvedigital.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ImprovedigitalImpExtImprovedigital {

    @JsonProperty("placementId")
    Integer placementId;
}
