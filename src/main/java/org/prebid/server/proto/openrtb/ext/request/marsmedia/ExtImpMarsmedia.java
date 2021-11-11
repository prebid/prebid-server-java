package org.prebid.server.proto.openrtb.ext.request.marsmedia;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpMarsmedia {

    @JsonProperty("zoneId")
    String zoneId;
}
