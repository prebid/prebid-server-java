package org.prebid.server.proto.openrtb.ext.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

/**
 * Defines the contract for bidresponse.ext.prebid.fledge (.auctionconfigs[])
 */
@Value(staticConstructor = "of")
public class ExtBidResponseFledge {

    @JsonProperty("auctionconfigs")
    List<FledgeAuctionConfig> auctionConfigs;
}
