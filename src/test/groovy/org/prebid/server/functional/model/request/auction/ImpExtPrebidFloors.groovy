package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString
import org.prebid.server.functional.model.Currency

@ToString(includeNames = true, ignoreNulls = true)
class ImpExtPrebidFloors {

    String floorRule
    BigDecimal floorRuleValue
    BigDecimal floorValue
    BigDecimal floorMin
    Currency floorMinCur
}
