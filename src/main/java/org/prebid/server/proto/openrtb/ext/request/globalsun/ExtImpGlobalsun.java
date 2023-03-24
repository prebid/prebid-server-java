package org.prebid.server.proto.openrtb.ext.request.globalsun;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class ExtImpGlobalsun {

    @JsonProperty("placementId")
    String placementId;

}
