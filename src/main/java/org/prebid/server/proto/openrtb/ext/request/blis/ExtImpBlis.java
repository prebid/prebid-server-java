package org.prebid.server.proto.openrtb.ext.request.blis;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpBlis {

    @JsonProperty("spid")
    String supplyId;
}
