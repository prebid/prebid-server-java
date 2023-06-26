package org.prebid.server.proto.openrtb.ext.request.motoril;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpMotorik {

    @JsonProperty("accountId")
    String accountId;

    @JsonProperty("placementId")
    String placementId;
}
