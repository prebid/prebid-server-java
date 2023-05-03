package org.prebid.server.functional.model.request.auction

enum ActivityType {

    SYNC_USER, FETCH_BIDS, ENRICH_UFPD, REPORT_ANALYTICS, TRANSMIT_UFPD, TRANSMIT_PRECISE_GEO


    String getMetricValue() {
        name().toLowerCase()
    }
}
