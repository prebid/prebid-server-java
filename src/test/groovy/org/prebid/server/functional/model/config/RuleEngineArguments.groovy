package org.prebid.server.functional.model.config

import org.prebid.server.functional.model.bidder.BidderName

class RuleEngineArguments {

    List<BidderName> bidders
    Integer seatNonBid
    Boolean ifSyncedId
    String analyticsValue
}
