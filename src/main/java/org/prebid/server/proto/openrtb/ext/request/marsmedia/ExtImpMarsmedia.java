package org.prebid.server.proto.openrtb.ext.request.marsmedia;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.marsmedia
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpMarsmedia {

    @JsonProperty("zoneId")
    String zone;
}
