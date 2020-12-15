package org.prebid.server.proto.openrtb.ext.request.synacormedia;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidRequest.ext
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtRequestSynacormedia {

    @JsonProperty("seatId")
    String seatId;
}
