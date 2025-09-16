package org.prebid.server.functional.model.response.currencyrates

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class CurrencyRatesResponse {

    Boolean active

    String source

    Long fetchingIntervalNs

    String lastUpdated

    Map<String, Map<String, BigDecimal>> rates
}
