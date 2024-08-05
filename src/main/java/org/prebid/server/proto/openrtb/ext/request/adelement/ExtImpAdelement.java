package org.prebid.server.proto.openrtb.ext.request.adelement;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class ExtImpAdelement {

    @JsonProperty("supply_id")
    String supplyId;
}
