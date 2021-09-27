package org.prebid.server.proto.openrtb.ext.request.adview;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

@Value
@AllArgsConstructor(staticName = "of")
public class ExtImpAdview {

    @JsonProperty("placementId")
    String masterTagId;

    @JsonProperty("accountId")
    String accountId;
}
