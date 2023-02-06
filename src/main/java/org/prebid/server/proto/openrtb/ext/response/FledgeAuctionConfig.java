package org.prebid.server.proto.openrtb.ext.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

/**
 * Defines the contract for bidresponse.ext.prebid.fledge.auctionconfigs[] elements
 */
@Value
@Builder(toBuilder = true)
public class FledgeAuctionConfig {

    @JsonProperty("impid")
    String impId;

    String bidder;

    String adapter;

    ObjectNode config;
}
