package org.prebid.server.proto.openrtb.ext.request.ccx;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpCcx {

    @JsonProperty("placementId")
    Integer placementId;
}
