package org.prebid.server.functional.model.request.activitie

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

import static org.prebid.server.functional.model.bidder.BidderName.GENERIC


@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy)
class AllowActivities {
    Activity syncUser;
    Activity fetchBid;
    Activity enrichUfpd;
    Activity reportAnalytics;
    Activity transmitEids;
    Activity transmitUfpd;
    Activity transmitGeo;

    static AllowActivities getDefaultAllowActivities() {
        new AllowActivities()
    }


}
