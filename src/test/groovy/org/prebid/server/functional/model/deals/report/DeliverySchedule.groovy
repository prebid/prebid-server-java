package org.prebid.server.functional.model.deals.report

import groovy.transform.ToString

import java.time.ZonedDateTime

@ToString(includeNames = true, ignoreNulls = true)
class DeliverySchedule {

    String planId
    ZonedDateTime planStartTimeStamp
    ZonedDateTime planExpirationTimeStamp
    ZonedDateTime planUpdatedTimeStamp
    Set<Token> tokens
}
