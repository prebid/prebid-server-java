package org.prebid.server.proto.openrtb.ext.request.invibes;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.request.invibes.model.InvibesDebug;

@Value(staticConstructor = "of")
public class ExtImpInvibes {

    @JsonProperty("placementId")
    String placementId;

    @JsonProperty("domainId")
    Integer domainId;

    @JsonProperty("debug")
    InvibesDebug debug;
}
