package org.prebid.server.bidder.medianet.model.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class InterestGroupAuctionSeller {

    @JsonProperty(value = "impid")
    String impId;

    ObjectNode config;
}
