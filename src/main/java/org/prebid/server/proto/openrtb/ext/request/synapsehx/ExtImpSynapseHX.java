package org.prebid.server.proto.openrtb.ext.request.synapsehx;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpSynapseHX {

    @JsonProperty("tenantId")
    String tenantId;
}
