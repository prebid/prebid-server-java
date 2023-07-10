package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString
import org.prebid.server.functional.model.bidder.BidderName
import org.prebid.server.functional.model.request.GppSectionId

@ToString(includeNames = true, ignoreNulls = true)
class Condition {

    List<GppSectionId> gppSig
    List<ConditionType> componentType
    List<String> componentName
    List<Integer> gppSid
    List<String> geo
    String gpc

    static Condition getBaseCondition(String componentName = BidderName.GENERIC.value) {
        new Condition().tap {
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
