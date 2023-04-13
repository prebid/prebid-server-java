package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.ToString

import static org.prebid.server.functional.model.request.auction.ActivityRule.Priority.DEFAULT
import static ActivityRule.getDefaultActivityRule

@ToString(includeNames = true, ignoreNulls = true)
class Activity {

    @JsonProperty("default")
    Boolean defaultAction
    List<ActivityRule> rules

    static Activity getDefaultActivity(isDefaultAction = true, rules = [defaultActivityRule] ) {
        new Activity().tap {
            it.defaultAction = isDefaultAction
            it.rules = rules
        }
    }

    static Activity getActivityWithRules(List<Condition> conditions, Boolean isAllowed) {
        getDefaultActivity(true, conditions.collect { singleCondition ->
            getDefaultActivityRule(DEFAULT, singleCondition,isAllowed)
        })
    }
}
