package org.prebid.server.proto.openrtb.ext.request.kubient;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.kubient
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpKubient {

    @JsonProperty("zoneid")
    String zoneId;
}
