package org.prebid.server.bidder.adhese.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.iab.openrtb.response.BidResponse;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class AdheseBid {

    String origin;

    @JsonProperty("originData")
    BidResponse originData;

    @JsonProperty("originInstance")
    String originInstance;

    String body;

    String height;

    String width;

    Prebid extension;
}
