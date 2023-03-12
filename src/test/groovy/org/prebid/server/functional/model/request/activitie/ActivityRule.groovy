package org.prebid.server.functional.model.request.activitie

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@EqualsAndHashCode
class ActivityRule {
    int priority;
    Condition condition;
    boolean allow;

    static ActivityRule getDefaultActivityRule() {
        new ActivityRule().tap {
        priority = 10
        condition = Condition.defaultCondition
        allow = true
        }
    }

}
