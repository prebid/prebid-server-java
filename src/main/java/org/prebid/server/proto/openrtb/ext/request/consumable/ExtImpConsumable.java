package org.prebid.server.proto.openrtb.ext.request.consumable;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.consumable
 */
@Value(staticConstructor = "of")
public class ExtImpConsumable {

    @JsonProperty("networkId")
    Integer networkId;

    @JsonProperty("siteId")
    Integer siteId;

    @JsonProperty("unitId")
    Integer unitId;

    @JsonProperty("unitName")
    String unitName;

    @JsonProperty("placementId")
    String placementId;
}
