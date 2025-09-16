package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.KebabCaseStrategy)
class AccountAnalyticsConfig {

    Map<String, Boolean> auctionEvents
    Boolean allowClientDetails
    AnalyticsModule modules

    @JsonProperty("auction_events")
    Map<String, Boolean> auctionEventsSnakeCase
}
