package org.prebid.server.bidder.kargo.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value
public class KargoExtBid {

    @JsonProperty("mediaType")
    String mediaType;
}
