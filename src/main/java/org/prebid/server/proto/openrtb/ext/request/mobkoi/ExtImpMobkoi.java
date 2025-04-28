package org.prebid.server.proto.openrtb.ext.request.mobkoi;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.mobkoi
 */
@Value(staticConstructor = "of")
public class ExtImpMobkoi {

    @JsonProperty("placementId")
    String placementId;

    @JsonProperty("adServerBaseUrl")
    String adServerBaseUrl;
}
