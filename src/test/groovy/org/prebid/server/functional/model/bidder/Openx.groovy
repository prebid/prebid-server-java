package org.prebid.server.functional.model.bidder

class Openx implements BidderAdapter {

    String unit
    String delDomain
    String platform
    Integer customFloor
    Map customParams

    static Openx getDefaultOpenX() {
        new Openx().tap {
            it.unit = "900"
            it.platform = UUID.randomUUID().toString()
            it.customParams = ["Any": "Any"]
        }
    }
}
