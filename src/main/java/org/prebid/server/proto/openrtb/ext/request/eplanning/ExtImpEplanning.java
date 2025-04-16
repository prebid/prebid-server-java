package org.prebid.server.proto.openrtb.ext.request.eplanning;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.eplanning
 */
@Value(staticConstructor = "of")
public class ExtImpEplanning {

    @JsonProperty("ci")
    String clientId;

    String adunitCode;
}
