package org.prebid.server.proto.openrtb.ext.request.silvermob;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidRequest.imp[i].ext.silvermob
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpSilvermob {

    @JsonProperty("zoneid")
    String zoneId;

    String host;
}
