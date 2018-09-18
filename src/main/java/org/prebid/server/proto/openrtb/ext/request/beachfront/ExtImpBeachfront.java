package org.prebid.server.proto.openrtb.ext.request.beachfront;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpBeachfront {

    @JsonProperty("appId")
    String appId;

    Float bidfloor;
}
