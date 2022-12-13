package org.prebid.server.bidder.openx.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.util.Map;

@Value
@AllArgsConstructor(staticName = "of")
@Builder
public class OpenxBidResponseExt {

    @JsonProperty("fledge_auction_configs")
    Map<String, ObjectNode> fledgeAuctionConfigs;
}
