package org.prebid.server.proto.openrtb.ext.request.alliancegravity;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpAllianceGravity {

    @JsonProperty("srid")
    String srId;
}
