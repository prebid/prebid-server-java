package org.prebid.server.bidder.algorix.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class AlgorixResponseBidExt {

    @JsonProperty("mediaType")
    String mediaType;
}
