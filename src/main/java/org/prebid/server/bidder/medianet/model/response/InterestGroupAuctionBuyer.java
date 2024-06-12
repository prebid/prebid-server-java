package org.prebid.server.bidder.medianet.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;

@Value
public class InterestGroupAuctionBuyer {

    String origin;

    @JsonProperty("maxbid")
    Double maxBid;

    @JsonProperty("cur")
    String currency;

    @JsonProperty("pbs")
    String buyerSignals;

    @JsonProperty("ps")
    ObjectNode prioritySignals;
}
