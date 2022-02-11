package org.prebid.server.functional.model.deals.lineitem

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class Price {

    BigDecimal cpm
    String currency

    static getDefaultPrice() {
        new Price(cpm: 0.01,
                currency: "USD"
        )
    }
}
