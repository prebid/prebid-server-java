package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class PreferredBidders {

    GenericPreferredBidder generic
    GenericPreferredBidder bidder
}
