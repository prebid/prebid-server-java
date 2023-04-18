package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.ToString
import org.prebid.server.functional.model.bidder.BidderName

@ToString(includeNames = true, ignoreNulls = true)
class Condition {

    List<ConditionType> componentType
    List<String> componentName

    static Condition getBaseCondition(String componentName = BidderName.GENERIC) {
        new Condition(componentName: [componentName], componentType: [ConditionType.BIDDER])
    }

    static Condition getBaseCondition(BidderName componentName) {
        getBaseCondition(componentName.value)
    }


    enum ConditionType {
        BIDDER("bidder"),
        GENERAL_MODULE("general"),
        RTD_MODULE("rtd"),
        USER_ID_MODULE("userid"),
        ANALYTICS("analytics"),
        EMPTY("")

        final String name

        private ConditionType(String name) {
            this.name = name
        }

        @JsonValue
        String getName() {
            return name.toLowerCase()
        }
    }
}
