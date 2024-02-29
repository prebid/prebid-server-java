package org.prebid.server.proto.openrtb.ext.request.freewheelssp;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpFreewheelSSP {

    @JsonProperty("zoneId")
    String zoneId;
}
