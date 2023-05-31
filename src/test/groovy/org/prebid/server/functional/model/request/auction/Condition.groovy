package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString
import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.request.GppSectionId

@ToString(includeNames = true, ignoreNulls = true)
class Condition {

    List<Integer> gppSid
    List<ConditionType> componentType
    List<String> componentName

    static Condition getBaseCondition(String componentName = BidderName.GENERIC.value) {
        new Condition().tap {
            it.gppSid = [GppSectionId.TCF_EU_V2.getIntValue()]
            it.componentType = [ConditionType.BIDDER]
            it.componentName = [componentName]
        }
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
