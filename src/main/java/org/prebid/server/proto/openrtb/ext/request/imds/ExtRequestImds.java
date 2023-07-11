package org.prebid.server.proto.openrtb.ext.request.imds;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidRequest.ext
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtRequestImds {

    @JsonProperty("seatId")
    String seatId;
}
