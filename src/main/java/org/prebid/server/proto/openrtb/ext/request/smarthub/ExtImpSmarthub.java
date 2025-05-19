package org.prebid.server.proto.openrtb.ext.request.smarthub;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpSmarthub {

    @JsonProperty("partnerName")
    String partnerName;

    String seat;

    String token;
}
