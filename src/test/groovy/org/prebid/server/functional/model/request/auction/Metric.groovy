package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class Metric {

    String type
    BigDecimal value
    String vendor
}
