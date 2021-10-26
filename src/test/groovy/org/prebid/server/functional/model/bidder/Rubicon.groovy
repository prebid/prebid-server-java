package org.prebid.server.functional.model.bidder

import org.prebid.server.functional.util.PBSUtils

class Rubicon implements BidderAdapter {

    Integer accountId
    Integer siteId
    Integer zoneId

    static Rubicon getDefault() {
        new Rubicon().tap {
            accountId = PBSUtils.randomNumber
            siteId = PBSUtils.randomNumber
            zoneId = PBSUtils.randomNumber
        }
    }
}
