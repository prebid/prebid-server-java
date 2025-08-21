package org.prebid.server.bidder.pubmatic.model.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class PubmaticMarketplace {

    @JsonProperty("allowedbidders")
    List<String> allowedBidders;
}
