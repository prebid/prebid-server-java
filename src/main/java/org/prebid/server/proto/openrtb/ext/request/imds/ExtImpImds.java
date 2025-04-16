package org.prebid.server.proto.openrtb.ext.request.imds;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.imds
 */
@Value(staticConstructor = "of")
public class ExtImpImds {

    @JsonProperty("seatId")
    String seatId;

    @JsonProperty("tagId")
    String tagId;
}
