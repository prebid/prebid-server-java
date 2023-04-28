package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class Activity {

    @JsonProperty("default")
    Boolean defaultAction
    List<ActivityRule> rules

    static Activity getDefaultActivity(rules = [ActivityRule.defaultActivityRule], isDefaultAction = true) {
        new Activity().tap {
            it.defaultAction = isDefaultAction
            it.rules = rules
        }
    }
}
