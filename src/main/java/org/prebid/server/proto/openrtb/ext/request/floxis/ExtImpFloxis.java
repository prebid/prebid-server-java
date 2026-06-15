package org.prebid.server.proto.openrtb.ext.request.floxis;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpFloxis {

    @JsonProperty("seat")
    String seat;

    @JsonProperty("region")
    String region;

    @JsonProperty("partner")
    String partner;
}
