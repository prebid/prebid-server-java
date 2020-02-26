package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Defines the contract for bidrequest.imp[i].ext.prebid
 */
@Builder
@Value
public class ExtImpPrebid {

    /**
     * Defines the contract for bidrequest.imp[i].ext.prebid.storedrequest
     */
    ExtStoredRequest storedrequest;

    /**
     * Defines the contract for bidrequest.imp[i].ext.prebid.options
     */
    ExtOptions options;

    /**
     * Defines the contract for bidrequest.imp[i].ext.prebid.storedAuctionResponse
     */
    @JsonProperty("storedauctionresponse")
    ExtStoredAuctionResponse storedAuctionResponse;

    /**
     * Defines the contract for bidrequest.imp[i].ext.prebid.storedBidResponse
     */
    @JsonProperty("storedbidresponse")
    List<ExtStoredBidResponse> storedBidResponse;

    /**
     * Defines the contract for bidrequest.imp[i].ext.prebid.is_rewarded_inventory
     */
    Boolean isRewardedInventory;
}
