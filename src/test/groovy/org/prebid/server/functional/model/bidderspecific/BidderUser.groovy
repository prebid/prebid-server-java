package org.prebid.server.functional.model.bidderspecific

import groovy.transform.ToString
import org.prebid.server.functional.model.request.auction.User

@ToString(includeNames = true, ignoreNulls = true)
class BidderUser extends User {

    BidderUserExt ext
}
