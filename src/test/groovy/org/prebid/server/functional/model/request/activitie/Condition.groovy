package org.prebid.server.functional.model.request.activitie

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC

@ToString(includeNames = true, ignoreNulls = true)
@EqualsAndHashCode
class Condition {
    Component componentType
    Component componentName

    @ToString(includeNames = true, ignoreNulls = true)
    @EqualsAndHashCode
    static class Component {
        @JsonProperty("in")
        List<String> xIn
        @JsonProperty("notin")
        List<String> notIn

        static Component getDefaultComponent() {
            new Component(xIn: [GENERIC.value], notIn: null)
        }
    }

    static Condition getDefaultCondition() {
        new Condition(componentName: Component.defaultComponent)
    }

}
