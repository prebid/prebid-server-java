package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

/**
 * Defines the contract for bidrequest.imp[i].ext.prebid
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpPrebid {

    /**
     * Defines the contract for bidrequest.imp[i].ext.prebid.storedrequest
     */
    ExtStoredRequest storedrequest;

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

    @JsonProperty("is_rewarded_inventory")
    Boolean isRewardedInventory;

    public static ExtImpPrebid of(ExtStoredRequest storedrequest,
                                  ExtStoredAuctionResponse storedAuctionResponse,
                                  List<ExtStoredBidResponse> storedBidResponse) {
        return ExtImpPrebid.of(storedrequest, storedAuctionResponse, storedBidResponse, null);
    }
}
