package org.prebid.server.proto.openrtb.ext.request.seedingalliance;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpSeedingAlliance {

    @JsonProperty("adUnitId")
    String adUnitId;

    @JsonProperty("seatId")
    String seatId;

}
