package org.prebid.server.functional.model.deals.report

import groovy.transform.ToString

import java.time.ZonedDateTime

@ToString(includeNames = true, ignoreNulls = true)
class LineItemStatusReport {

    String lineItemId
    DeliverySchedule deliverySchedule
    Long spentTokens
    ZonedDateTime readyToServeTimestamp
    Long pacingFrequency
    String accountId
    Map target
}
