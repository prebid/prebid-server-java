package org.prebid.server.proto.openrtb.ext.request.interactiveoffers;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.interactiveoffers
 */
@Value(staticConstructor = "of")
public class ExtImpInteractiveoffers {

    @JsonProperty("partnerId")
    String partnerId;
}
