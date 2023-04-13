package org.prebid.server.functional.model.request.auction


import groovy.transform.ToString

import static org.prebid.server.functional.model.request.auction.ActivityType.*

@ToString(includeNames = true, ignoreNulls = true)
class AllowActivities {

    Activity syncUser
    Activity fetchBids
    Activity enrichUfpd
    Activity reportAnalytics
    Activity transmitUfpd
    Activity transmitPreciseGeo

    static AllowActivities getDefaultAllowActivities(ActivityType activityType, Activity activity) {
        new AllowActivities().tap {
            switch (activityType) {
                case SYNC_USER:
                    return syncUser = activity
                case FETCH_BIDS:
                    return fetchBids = activity
                case ENRICH_UFPD:
                    return enrichUfpd = activity
                case REPORT_ANALYTICS:
                    return reportAnalytics = activity
                case TRANSMIT_UFPD:
                    return transmitUfpd = activity
                case TRANSMIT_PRECISE_GEO:
                    return transmitPreciseGeo = activity
            }
        }
    }
}
