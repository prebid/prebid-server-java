package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString
import org.prebid.server.functional.model.bidder.BidderName

@ToString(includeNames = true, ignoreNulls = true)
class Condition {

    List<ConditionType> componentType
    List<String> componentName

    static Condition getBaseCondition(String componentName = BidderName.GENERIC) {
        new Condition(componentName: [componentName], componentType: [ConditionType.BIDDER])
    }

    enum ConditionType {
        BIDDER("bidder"),
        GENERAL_MODULE("general"),
        RTD_MODULE("rtd"),
        ANALYTICS("analytics")

        final String name

        private ConditionType(String name) {
            this.name = name
        }
    }
}
