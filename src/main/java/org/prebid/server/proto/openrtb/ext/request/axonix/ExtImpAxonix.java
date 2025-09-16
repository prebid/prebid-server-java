package org.prebid.server.proto.openrtb.ext.request.axonix;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpAxonix {

    @JsonProperty("supplyId")
    String supplyId;
}
