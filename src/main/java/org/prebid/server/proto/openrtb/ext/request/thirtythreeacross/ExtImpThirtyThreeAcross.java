package org.prebid.server.proto.openrtb.ext.request.thirtythreeacross;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.33across
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpThirtyThreeAcross {

    @JsonProperty("siteId")
    String siteId;

    @JsonProperty("zoneId")
    String zoneId;

    @JsonProperty("productId")
    String productId;
}
