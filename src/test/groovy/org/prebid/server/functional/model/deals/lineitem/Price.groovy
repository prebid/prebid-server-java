package org.prebid.server.functional.model.deals.lineitem

import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@ToString(includeNames = true, ignoreNulls = true)
class Price {

    BigDecimal cpm
    String currency

    static getDefaultPrice() {
        new Price(cpm: PBSUtils.randomPrice, currency: "USD")
    }
}
