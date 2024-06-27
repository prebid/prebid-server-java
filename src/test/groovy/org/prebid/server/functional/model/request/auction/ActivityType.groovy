package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonValue
import org.prebid.server.functional.util.Case
import org.prebid.server.functional.util.PBSUtils

enum ActivityType {

    SYNC_USER("syncUser"),
    FETCH_BIDS("fetchBids"),
    ENRICH_UFPD("enrichUfpd"),
    REPORT_ANALYTICS("reportAnalytics"),
    TRANSMIT_UFPD("transmitUfpd"),
    TRANSMIT_PRECISE_GEO("transmitPreciseGeo"),
    TRANSMIT_TID("transmitTid"),
    TRANSMIT_EIDS("transmitEids")

    final String value

    ActivityType(String value) {
        this.value = value
    }

    String getMetricValue() {
        name().toLowerCase()
    }

    @JsonValue
    String getValue() {
        def type = PBSUtils.getRandomEnum(Case.class)
        if (type == Case.KEBAB) {
            PBSUtils.convertCase(value, Case.KEBAB)
        } else if (type == Case.SNAKE) {
            PBSUtils.convertCase(value, Case.SNAKE)
        }
        return value
    }
}
