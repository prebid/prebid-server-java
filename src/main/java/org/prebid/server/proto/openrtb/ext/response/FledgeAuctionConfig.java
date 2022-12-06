package org.prebid.server.proto.openrtb.ext.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;

/**
 * Defines the contract for bidresponse.ext.prebid.fledge.auctionconfigs[] elements
 */
@Builder
public class FledgeAuctionConfig {

    @JsonProperty("impid")
    String impId;
    String bidder;
    String adapter;
    JsonNode config;
}
