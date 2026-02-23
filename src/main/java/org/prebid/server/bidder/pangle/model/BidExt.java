package org.prebid.server.bidder.pangle.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class BidExt {

    @JsonProperty("adtype")
    Integer adType;
}
