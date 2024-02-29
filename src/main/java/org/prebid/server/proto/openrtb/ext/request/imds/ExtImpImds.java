package org.prebid.server.proto.openrtb.ext.request.imds;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.imds
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpImds {

    @JsonProperty("seatId")
    String seatId;

    @JsonProperty("tagId")
    String tagId;
}
