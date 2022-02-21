package org.prebid.server.bidder.algorix.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class AlgorixBidExt {

    @JsonProperty("mediaType")
    String mediaType;
}
