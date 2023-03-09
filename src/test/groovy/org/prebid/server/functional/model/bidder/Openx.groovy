package org.prebid.server.functional.model.bidder

import org.prebid.server.functional.util.PBSUtils

class Openx implements BidderAdapter {

    String unit
    String delDomain
    String platform
    Integer customFloor
    Map<String, Map> customParams

    static Openx getDefaultOpenx() {
        new Openx().tap {
            it.unit = PBSUtils.randomNumber
            it.platform = UUID.randomUUID().toString()
        }
    }
}
