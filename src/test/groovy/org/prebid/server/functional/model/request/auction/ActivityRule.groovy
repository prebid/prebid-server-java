package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.ToString

import static org.prebid.server.functional.model.request.auction.ActivityRule.Priority.DEFAULT

@ToString(includeNames = true, ignoreNulls = true)
class ActivityRule {

    Priority priority
    Condition condition
    Boolean allow
    List<String> privacyRegs

    static ActivityRule getDefaultActivityRule(priority = DEFAULT, condition = Condition.baseCondition, allow = true) {
        new ActivityRule().tap {
            it.priority = priority
            it.condition = condition
            it.allow = allow
        }
    }

    enum Priority {
        HIGHEST(1),
        DEFAULT(Integer.MAX_VALUE / 2 as Integer),
        LOWEST(Integer.MAX_VALUE),
        INVALID(Integer.MIN_VALUE)

        final Integer value

        Priority(Integer value) {
            this.value = value
        }

        @JsonValue
        Integer getValue() {
            value
        }
    }
}
