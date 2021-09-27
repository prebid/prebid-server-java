package org.prebid.server.proto.openrtb.ext.request.smarthub;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpSmarthub {

    @JsonProperty("partnerName")
    String partnerName;

    String seat;

    String token;
}
