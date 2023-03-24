package org.prebid.server.bidder.globalsun.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class GlobalsunImpExtBidder {

    String type;

    @JsonProperty(value = "placementId")
    String placementId;
}
