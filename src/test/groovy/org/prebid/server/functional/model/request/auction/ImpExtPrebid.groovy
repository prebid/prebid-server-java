package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
class ImpExtPrebid {

    Bidder bidder
    PrebidStoredRequest storedRequest
    StoredAuctionResponse storedAuctionResponse

    static ImpExtPrebid getDefaultImpExtPrebid() {
        new ImpExtPrebid().tap {
            bidder = Bidder.defaultBidder
        }
    }
}
