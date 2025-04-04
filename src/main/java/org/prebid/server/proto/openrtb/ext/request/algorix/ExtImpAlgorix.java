package org.prebid.server.proto.openrtb.ext.request.algorix;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

/**
 * Algorix Ext Imp
 */
@Value(staticConstructor = "of")
public class ExtImpAlgorix {

    String sid;

    String token;

    @JsonProperty("placementId")
    String placementId;

    @JsonProperty("appId")
    String appId;

    @JsonProperty("region")
    String region;
}
