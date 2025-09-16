package org.prebid.server.proto.openrtb.ext.request.triplelift;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.math.BigDecimal;

/**
 * Defines the contract for bidRequest.imp[i].ext.triplelift
 */
@Value(staticConstructor = "of")
public class ExtImpTriplelift {

    @JsonProperty("inventoryCode")
    String inventoryCode;

    BigDecimal floor;
}
