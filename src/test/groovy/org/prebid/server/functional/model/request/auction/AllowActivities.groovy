package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.ToString

import static org.prebid.server.functional.model.request.auction.ActivityType.ENRICH_UFPD
import static org.prebid.server.functional.model.request.auction.ActivityType.FETCH_BIDS
import static org.prebid.server.functional.model.request.auction.ActivityType.REPORT_ANALYTICS
import static org.prebid.server.functional.model.request.auction.ActivityType.SYNC_USER
import static org.prebid.server.functional.model.request.auction.ActivityType.TRANSMIT_EIDS
import static org.prebid.server.functional.model.request.auction.ActivityType.TRANSMIT_PRECISE_GEO
import static org.prebid.server.functional.model.request.auction.ActivityType.TRANSMIT_TID
import static org.prebid.server.functional.model.request.auction.ActivityType.TRANSMIT_UFPD

@ToString(includeNames = true, ignoreNulls = true)
class AllowActivities {

    Activity syncUser
    Activity fetchBids
    Activity enrichUfpd
    Activity reportAnalytics
    Activity transmitUfpd
    Activity transmitEids
    Activity transmitPreciseGeo
    Activity transmitTid

    //Different case for each activity
    @JsonProperty("sync-user")
    Activity syncUserKebabCase
    @JsonProperty("sync_user")
    Activity syncUserSnakeCase

    @JsonProperty("fetch-bids")
    Activity fetchBidsKebabCase
    @JsonProperty("fetch_bids")
    Activity fetchBidsSnakeCase

    @JsonProperty("enrich-ufpd")
    Activity enrichUfpdKebabCase
    @JsonProperty("enrich_ufpd")
    Activity enrichUfpdSnakeCase

    @JsonProperty("report-analytics")
    Activity reportAnalyticsKebabCase
    @JsonProperty("report_analytics")
    Activity reportAnalyticsSnakeCase

    @JsonProperty("transmit-ufpd")
    Activity transmitUfpdKebabCase
    @JsonProperty("transmit_ufpd")
    Activity transmitUfpdSnakeCase

    @JsonProperty("transmit-eids")
    Activity transmitEidsKebabCase
    @JsonProperty("transmit_eids")
    Activity transmitEidsSnakeCase

    @JsonProperty("transmit-precise-geo")
    Activity transmitPreciseGeoKebabCase
    @JsonProperty("transmit_precise_geo")
    Activity transmitPreciseGeoSnakeCase

    @JsonProperty("transmit-tid")
    Activity transmitTidKebabCase
    @JsonProperty("transmit_tid")
    Activity transmitTidSnakeCase

    static AllowActivities getDefaultAllowActivities(ActivityType activityType, Activity activity) {
        new AllowActivities().tap {
            switch (activityType) {
                case SYNC_USER:
                    return syncUser = activity
                case FETCH_BIDS:
                    return fetchBids = activity
                case ENRICH_UFPD:
                    return enrichUfpd = activity
                case TRANSMIT_EIDS:
                    return transmitEids = activity
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
