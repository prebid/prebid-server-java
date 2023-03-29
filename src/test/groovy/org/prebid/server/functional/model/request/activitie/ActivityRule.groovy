package org.prebid.server.functional.model.request.activitie

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import lombok.Value

@Value(staticConstructor = "of")
@ToString(includeNames = true, ignoreNulls = true)
@EqualsAndHashCode
class ActivityRule {
    int priority = 10
    Condition condition
    boolean allow = false

    static ActivityRule getDefaultActivityRule(
            condition = Condition.defaultCondition,
            allow = true
    ) {
        new ActivityRule().tap {
            it.condition = condition
            it.allow = allow
        }
    }

}
