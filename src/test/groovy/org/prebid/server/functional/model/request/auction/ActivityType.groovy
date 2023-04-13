package org.prebid.server.functional.model.request.auction

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
enum ActivityType {
    SYNC_USER,
    FETCH_BIDS,
    ENRICH_UFPD,
    REPORT_ANALYTICS,
    TRANSMIT_UFPD,
    TRANSMIT_PRECISE_GEO
}
