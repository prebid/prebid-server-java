package org.prebid.server.functional.model.bidderspecific

import groovy.transform.ToString
import org.prebid.server.functional.model.request.auction.UserExt

@ToString(includeNames = true, ignoreNulls = true)
class BidderUserExt extends UserExt {

    Rp rp
}
