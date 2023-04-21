package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.ToString

import static org.prebid.server.functional.model.request.auction.ActivityRule.Priority.DEFAULT

@ToString(includeNames = true, ignoreNulls = true)
class Activity {

    @JsonProperty("default")
    Boolean defaultAction
    List<ActivityRule> rules

    static Activity getDefaultActivity(isDefaultAction = true, rules = [ActivityRule.defaultActivityRule]) {
        new Activity().tap {
            it.defaultAction = isDefaultAction
            it.rules = rules
        }
    }

    static Activity getActivityWithRules(Condition conditions, Boolean isAllowed) {
        getDefaultActivity(true, [ActivityRule.getDefaultActivityRule(DEFAULT, conditions, isAllowed)])
    }
}
