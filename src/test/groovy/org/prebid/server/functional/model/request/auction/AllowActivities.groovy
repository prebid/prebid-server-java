package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString

import static org.prebid.server.functional.model.request.auction.ActivityType.TRANSMIT_PRECISE_GEO
import static org.prebid.server.functional.model.request.auction.ActivityType.TRANSMIT_TID
import static org.prebid.server.functional.model.request.auction.ActivityType.TRANSMIT_UFPD
import static org.prebid.server.functional.model.request.auction.ActivityType.REPORT_ANALYTICS
import static org.prebid.server.functional.model.request.auction.ActivityType.ENRICH_UFPD
import static org.prebid.server.functional.model.request.auction.ActivityType.FETCH_BIDS
import static org.prebid.server.functional.model.request.auction.ActivityType.SYNC_USER

@ToString(includeNames = true, ignoreNulls = true)
class AllowActivities {

    Activity syncUser
    Activity fetchBids
    Activity enrichUfpd
    Activity reportAnalytics
    Activity transmitUfpd
    Activity transmitPreciseGeo
    Activity transmitTid

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
                case TRANSMIT_TID:
                    return transmitTid = activity
            }
        }
    }
}
