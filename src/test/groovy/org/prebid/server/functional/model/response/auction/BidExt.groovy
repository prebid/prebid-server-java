package org.prebid.server.functional.model.response.auction

import groovy.transform.ToString
import org.prebid.server.functional.model.Currency

@ToString(includeNames = true, ignoreNulls = true)
class BidExt {

    Prebid prebid
    BigDecimal origbidcpm
    Currency origbidcur
    DsaResponse dsa
}
