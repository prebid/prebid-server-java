package org.prebid.server.proto.openrtb.ext.request.eplanning;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.eplanning
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpEplanning {

    @JsonProperty("ci")
    String clientId;

    String adunitCode;
}
