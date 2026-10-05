package org.prebid.server.functional.model.bidder

import org.prebid.server.functional.util.PBSUtils

class Magnite implements BidderAdapter {

    Integer accountId
    Integer siteId
    Integer zoneId

    static Magnite getDefaultMagnite() {
        new Magnite().tap {
            accountId = PBSUtils.randomNumber
            siteId = PBSUtils.randomNumber
            zoneId = PBSUtils.randomNumber
        }
    }
}
