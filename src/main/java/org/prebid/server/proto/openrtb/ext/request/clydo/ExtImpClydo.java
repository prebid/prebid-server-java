package org.prebid.server.proto.openrtb.ext.request.clydo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpClydo {

    @JsonProperty("partnerId")
    String partnerId;

    @JsonProperty("region")
    String region;
}
