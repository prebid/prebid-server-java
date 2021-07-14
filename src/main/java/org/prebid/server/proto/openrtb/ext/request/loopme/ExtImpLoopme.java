package org.prebid.server.proto.openrtb.ext.request.loopme;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.loopme
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpLoopme {

    @JsonProperty("accountId")
    String accountId;
}
