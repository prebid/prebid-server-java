package org.prebid.server.functional.model.deals.register

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class RegisterRequest {

    BigDecimal healthIndex
    Status status
    String hostInstanceId
    String region
    String vendor
}
