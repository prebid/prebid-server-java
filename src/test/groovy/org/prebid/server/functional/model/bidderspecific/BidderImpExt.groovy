package org.prebid.server.functional.model.bidderspecific

import groovy.transform.ToString
import org.prebid.server.functional.model.bidder.GeneralBidderAdapter
import org.prebid.server.functional.model.request.auction.ImpExt

@ToString(includeNames = true, ignoreNulls = true)
class BidderImpExt extends ImpExt {

    GeneralBidderAdapter bidder
    Rp rp
}
