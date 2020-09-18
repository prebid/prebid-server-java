package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Defines the contract for bidrequest.imp[i].ext.prebid
 */
@Builder(toBuilder = true)
@Value
public class ExtImpPrebid {

    /**
     * Defines the contract for bidrequest.imp[i].ext.prebid.storedrequest
     */
    ExtStoredRequest storedrequest;

    /**
     * Defines the contract for bidrequest.imp[i].ext.prebid.storedauctionresponse
     */
    @JsonProperty("storedauctionresponse")
    ExtStoredAuctionResponse storedAuctionResponse;

    /**
     * Defines the contract for bidrequest.imp[i].ext.prebid.storedbidresponse
     */
    @JsonProperty("storedbidresponse")
    List<ExtStoredBidResponse> storedBidResponse;

    /**
     * Defines the contract for bidrequest.imp[i].ext.prebid.options
     */
    ExtOptions options;

    /**
     * Defines the contract for bidrequest.imp[i].ext.prebid.is_rewarded_inventory
     */
    Integer isRewardedInventory;

    /**
     * Defines the contract for bidrequest.imp[i].ext.prebid.BIDDER
     */
    ObjectNode bidder;
}
