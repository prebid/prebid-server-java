package org.prebid.server.functional.model.bidder

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy)
class AppNexus implements BidderAdapter {

    Integer placementId
    String invCode
    String trafficSourceCode

    static AppNexus getDefault() {
        new AppNexus().tap {
            placementId = PBSUtils.randomNumber
            invCode = PBSUtils.randomString
            trafficSourceCode = PBSUtils.randomString
        }
    }
}
