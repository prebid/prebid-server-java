package org.prebid.server.functional.model.deals.report

import groovy.transform.ToString

import java.time.ZonedDateTime

@ToString(includeNames = true, ignoreNulls = true)
class DeliveryStatisticsReport {

    String reportId
    String instanceId
    String vendor
    String region
    Long clientAuctions
    Set<LineItemStatus> lineItemStatus
    ZonedDateTime reportTimeStamp
    ZonedDateTime dataWindowStartTimeStamp
    ZonedDateTime dataWindowEndTimeStamp
}
