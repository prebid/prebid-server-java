package org.prebid.server.proto.openrtb.ext.request.adelement;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpAdelement {

    @JsonProperty("supply_id")
    String supplyId;
}
