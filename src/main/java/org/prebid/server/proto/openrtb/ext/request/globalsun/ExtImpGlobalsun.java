package org.prebid.server.proto.openrtb.ext.request.globalsun;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpGlobalsun {

    @JsonProperty("placementId")
    String placementId;

}
