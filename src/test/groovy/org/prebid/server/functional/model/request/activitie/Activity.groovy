package org.prebid.server.functional.model.request.activitie

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import lombok.Value

@Value(staticConstructor = "of")
@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy)
class Activity {

    @JsonProperty("default")
    boolean defaultAction
    List<ActivityRule> rules

    static Activity getDefaultActivity(rules = [[ActivityRule.defaultActivityRule]]) {
        new Activity().tap {
            defaultAction = true
            it.rules = rules
        }
    }

    static Activity getActivityWithRules(List<Condition> conditions, boolean isAllowed) {
        getDefaultActivity(conditions.collect { singleCondition ->
            ActivityRule.getDefaultActivityRule(singleCondition,isAllowed)
        })
    }
}
