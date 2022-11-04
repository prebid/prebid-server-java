package org.prebid.server.functional.model.request.cookiesync

import org.prebid.server.functional.model.bidder.BidderName

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC
import static org.prebid.server.functional.model.request.cookiesync.FilterType.EXCLUDE

class MethodFilter {

    List<BidderName> bidders
    FilterType filter

    static MethodFilter getDefaultMethodFilter(){
        new MethodFilter().tap {
            bidders = [GENERIC]
            filter = EXCLUDE
        }
    }
}
