package org.prebid.server.bidder.ix.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;

@Value(staticConstructor = "of")
public class AuctionConfigExtBidResponse {

    @JsonProperty("bidId")
    String bidId;

    ObjectNode config;
}
