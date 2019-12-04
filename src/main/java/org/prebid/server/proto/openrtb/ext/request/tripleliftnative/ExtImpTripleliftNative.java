package org.prebid.server.proto.openrtb.ext.request.tripleliftnative;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.math.BigDecimal;

/**
 * Defines the contract for bidRequest.imp[i].ext.triplelift_native
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpTripleliftNative {

    @JsonProperty("inventoryCode")
    String inventoryCode;

    BigDecimal floor;
}

