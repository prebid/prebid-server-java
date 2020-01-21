package org.prebid.server.proto.openrtb.ext.request.facebook;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpFacebook {

    @JsonProperty("placementId")
    String placementId;

    @JsonProperty("publisherId")
    String publisherId;
}
