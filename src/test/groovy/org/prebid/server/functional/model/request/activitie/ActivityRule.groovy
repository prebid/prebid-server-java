package org.prebid.server.functional.model.request.activitie

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import lombok.Value

import static org.prebid.server.functional.model.request.activitie.ActivityRule.Priory.DEFAULT

@Value(staticConstructor = "of")
@ToString(includeNames = true, ignoreNulls = true)
@EqualsAndHashCode
class ActivityRule {

    Priory priority
    Condition condition
    boolean allow = true

    static ActivityRule getDefaultActivityRule(
            condition = Condition.baseCondition,
            allow = true
    ) {
        new ActivityRule().tap {
            it.priority = DEFAULT
            it.condition = condition
            it.allow = allow
        }
    }

    enum Priory {
        TOP(1),
        DEFAULT(10),
        LOW(15),
        INVALID(-1)

        final Integer value

        Priory(Integer value) {
            this.value = value
        }

        @JsonValue
        Integer getValue() {
            value
        }
    }
}
