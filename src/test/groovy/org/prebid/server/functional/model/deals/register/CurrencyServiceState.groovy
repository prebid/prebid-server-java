package org.prebid.server.functional.model.deals.register

import groovy.transform.ToString

import java.time.ZonedDateTime

@ToString(includeNames = true, ignoreNulls = true)
class CurrencyServiceState {

    ZonedDateTime lastUpdate
}
