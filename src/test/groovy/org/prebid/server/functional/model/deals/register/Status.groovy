package org.prebid.server.functional.model.deals.register

import groovy.transform.ToString
import org.prebid.server.functional.model.deals.report.DeliveryStatisticsReport

@ToString(includeNames = true, ignoreNulls = true)
class Status {

    CurrencyServiceState currencyRates
    DeliveryStatisticsReport dealsStatus
}
