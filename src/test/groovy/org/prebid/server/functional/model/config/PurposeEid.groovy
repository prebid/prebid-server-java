package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy)
class PurposeEid {

    Boolean requireConsent
    List<String> exceptions
    Boolean activityTransition
    @JsonProperty("require-consent")
    Boolean requireConsentKebabCase
    @JsonProperty("activity-transition")
    Boolean activityTransitionKebabCase
}
