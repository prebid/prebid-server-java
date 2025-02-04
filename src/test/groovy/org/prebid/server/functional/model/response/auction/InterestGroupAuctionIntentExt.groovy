package org.prebid.server.functional.model.response.auction

import groovy.transform.ToString
import org.prebid.server.functional.model.bidder.BidderName

@ToString(includeNames = true, ignoreNulls = true)
class InterestGroupAuctionIntentExt {

    BidderName bidder
    BidderName adapter
}
