package org.prebid.server.functional.model.deals.alert

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

import java.time.ZonedDateTime

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy)
class AlertEvent {

    String id
    Action action
    AlertPriority priority
    ZonedDateTime updatedAt
    String name
    String details
    AlertSource source
}
