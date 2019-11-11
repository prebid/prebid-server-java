package org.prebid.server.proto.openrtb.ext.request.synacormedia;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.synacormedia
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpSynacormedia {

    @JsonProperty("seatId")
    String seatId;
}
