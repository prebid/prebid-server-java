package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

import static org.prebid.server.functional.model.request.auction.ActivityRule.Priority.DEFAULT

@ToString(includeNames = true, ignoreNulls = true)
@EqualsAndHashCode
class ActivityRule {

    Priority priority
    Condition condition
    Boolean allow
    Object privacyRegs

    static ActivityRule getDefaultActivityRule(
            priority = DEFAULT,
            condition = Condition.baseCondition,
            allow = true) {

        new ActivityRule().tap {
            it.priority = priority
            it.condition = condition
            it.allow = allow
        }
    }

    enum Priority {
        HIGHEST(1),
        DEFAULT(10),
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
