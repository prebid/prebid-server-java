package org.prebid.server.proto.openrtb.ext.request.consumable;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.consumable
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpConsumable {

    @JsonProperty("networkId")
    Integer networkId;

    @JsonProperty("siteId")
    Integer siteId;

    @JsonProperty("unitId")
    Integer unitId;

    @JsonProperty("unitName")
    String unitName;
}
