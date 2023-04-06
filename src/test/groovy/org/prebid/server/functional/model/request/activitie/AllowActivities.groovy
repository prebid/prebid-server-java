package org.prebid.server.functional.model.request.activitie

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy)
class AllowActivities {

    Activity syncUser
    Activity fetchBid
    Activity enrichUfpd
    Activity reportAnalytics
    Activity transmitEids
    Activity transmitUfpd
    Activity transmitGeo

    static AllowActivities getDefaultAllowActivities(ActivityType activityType, Activity activity) {
        new AllowActivities().tap {
            switch (activityType) {
                case ActivityType.SYNC_USER:
                    return syncUser = activity
                case ActivityType.FETCH_BID:
                    return fetchBid = activity
                case ActivityType.ENRICH_UFPD:
                    return enrichUfpd = activity
                case ActivityType.REPORT_ANALYTICS:
                    return reportAnalytics = activity
                case ActivityType.TRANSMIT_EIDS:
                    return transmitEids = activity
                case ActivityType.TRANSMIT_UFPD:
                    return transmitUfpd = activity
                case ActivityType.TRANSMIT_GEO:
                    return transmitGeo = activity
            }
        }
    }

    enum ActivityType {
        SYNC_USER,
        FETCH_BID,
        ENRICH_UFPD,
        REPORT_ANALYTICS,
        TRANSMIT_EIDS,
        TRANSMIT_UFPD,
        TRANSMIT_GEO;
    }

}
