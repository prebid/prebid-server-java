package org.prebid.server.functional.model.request.activitie

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import lombok.Builder
import lombok.Value

@Builder
@Value(staticConstructor = "of")
@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy)
class Activity {

    @JsonProperty("default")
    boolean defaultAction;
    List<ActivityRule> rules;

    static Activity getDefaultActivityRule() {
        new Activity().tap {
            defaultAction = true
            rules = [ActivityRule.defaultActivityRule]
        }
    }
}
