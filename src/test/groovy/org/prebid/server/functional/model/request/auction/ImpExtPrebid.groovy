package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
class ImpExtPrebid {

    PrebidStoredRequest storedRequest
    StoredAuctionResponse storedAuctionResponse
    List<StoredBidResponse> storedBidResponse
    @JsonProperty("is_rewarded_inventory")
    Integer isRewardedInventory
    Bidder bidder
    ImpExtPrebidFloors floors
    Map passThrough

    static ImpExtPrebid getDefaultImpExtPrebid() {
        new ImpExtPrebid().tap {
            bidder = Bidder.defaultBidder
        }
    }
}
