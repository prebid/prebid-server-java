package org.prebid.server.proto.openrtb.ext.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;

/**
 * Defines the contract for bidresponse.ext.prebid.fledge.auctionconfigs[] elements
 */
@Value
@Builder(toBuilder = true)
public class FledgeConfig {

    @JsonProperty("impid")
    String impId;
    String bidder;
    String adapter;
    JsonNode config;
}
