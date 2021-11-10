package org.prebid.server.functional.model.bidderspecific

import groovy.transform.ToString
import org.prebid.server.functional.model.bidder.Generic
import org.prebid.server.functional.model.request.auction.ImpExt

@ToString(includeNames = true, ignoreNulls = true)
class BidderImpExt extends ImpExt {

    Generic bidder
}
