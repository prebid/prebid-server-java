package org.prebid.server.proto.openrtb.ext.request.interactiveoffers;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.interactiveoffers
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpInteractiveoffers {

    @JsonProperty("partnerId")
    String partnerId;
}
