package org.prebid.server.bidder.pubmatic.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;

import java.util.Map;

@Value(staticConstructor = "of")
public class PubmaticExtBidResponse {

    @JsonProperty("fledge_auction_configs")
    Map<String, ObjectNode> fledgeAuctionConfigs;

}
