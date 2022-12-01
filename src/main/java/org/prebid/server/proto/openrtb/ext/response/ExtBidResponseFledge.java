package org.prebid.server.proto.openrtb.ext.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Defines the contract for bidresponse.ext.prebid.fledge .auctionconfigs[]
 */
@Value
@AllArgsConstructor(staticName = "of")
@Builder(toBuilder = true)
public class ExtBidResponseFledge {

    @Builder
    public static class FledgeAuctionConfig {

        @JsonProperty("impid")
        String impId;
        String bidder;
        String adapter;
        JsonNode config;
    }

    @JsonProperty("auctionconfigs")
    List<FledgeAuctionConfig> auctionConfigs;
}
