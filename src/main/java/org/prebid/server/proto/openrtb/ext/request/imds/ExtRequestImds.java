package org.prebid.server.proto.openrtb.ext.request.imds;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

/**
 * Defines the contract for bidRequest.ext
 */
@Value(staticConstructor = "of")
public class ExtRequestImds {

    @JsonProperty("seatId")
    String seatId;
}
