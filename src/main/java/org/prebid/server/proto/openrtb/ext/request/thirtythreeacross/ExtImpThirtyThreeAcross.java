package org.prebid.server.proto.openrtb.ext.request.thirtythreeacross;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.33across
 */
@Value(staticConstructor = "of")
public class ExtImpThirtyThreeAcross {

    @JsonProperty("siteId")
    String siteId;

    @JsonProperty("zoneId")
    String zoneId;

    @JsonProperty("productId")
    String productId;
}
