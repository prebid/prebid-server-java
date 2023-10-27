package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import groovy.transform.EqualsAndHashCode

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy)
@EqualsAndHashCode
class ActivityInfrastructure {

    String description
    String activity
    ActivityInvocationPayload activityInvocationPayload
    RuleConfiguration ruleConfiguration
    Boolean allowByDefault
    Boolean allowed
    String result
    String region
    String country
}
