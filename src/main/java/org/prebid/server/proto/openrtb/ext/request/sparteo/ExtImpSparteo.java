package org.prebid.server.proto.openrtb.ext.request.sparteo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpSparteo {

    @JsonProperty("networkId")
    String networkId;
}
