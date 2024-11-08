package org.prebid.server.functional.model.request.auction

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@EqualsAndHashCode
class Range {

    BigDecimal max
    BigDecimal increment

    static Range getDefault(Integer max, BigDecimal increment) {
        new Range(max: max, increment: increment)
    }
}
