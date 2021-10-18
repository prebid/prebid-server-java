package org.prebid.server.proto.openrtb.ext.request.algorix;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Algorix Ext Imp
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpAlgorix {

    String sid;

    String token;

    @JsonProperty("placementId")
    String placementId;

    @JsonProperty("appId")
    String appId;
}
