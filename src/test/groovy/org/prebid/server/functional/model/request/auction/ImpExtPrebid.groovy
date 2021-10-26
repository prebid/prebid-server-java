package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class ImpExtPrebid {

    Bidder bidder

    static ImpExtPrebid getDefaultImpExtPrebid() {
        new ImpExtPrebid().tap {
            bidder = Bidder.defaultBidder
        }
    }
}
