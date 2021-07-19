package org.prebid.server.proto.openrtb.ext.request.axonix;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class ExtImpAxonix {

    @JsonProperty("supplyId")
    String supplyId;
}
