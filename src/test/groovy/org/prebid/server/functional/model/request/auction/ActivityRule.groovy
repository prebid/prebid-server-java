package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class ActivityRule {

    Condition condition
    Boolean allow

    static ActivityRule getDefaultActivityRule(condition = Condition.baseCondition, allow = true) {
        new ActivityRule().tap {
            it.condition = condition
            it.allow = allow
        }
    }
}
