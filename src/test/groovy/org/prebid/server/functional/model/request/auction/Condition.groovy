package org.prebid.server.functional.model.request.auction

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@EqualsAndHashCode
class Condition {

    Component componentType
    Component componentName

    static Condition getBaseCondition(Component component = Component.baseComponent) {
        new Condition(componentName: component)
    }

    enum ConditionType {
        BIDDER("bidder"),
        ANALITICS("analitics"),
        GENERAL_MODULE("general"),
        RTD_MODULE("rtd"),
        USER_ID_MODULE("userid"),
        ANALYTICS("analytics")

        final String name

        private ConditionType(String name) {
            this.name = name
        }

        String getName() {
            return name
        }
    }
}
