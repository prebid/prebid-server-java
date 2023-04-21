package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonValue
import org.apache.commons.text.CaseUtils

enum ActivityType {
    SYNC_USER, FETCH_BIDS, ENRICH_UFPD, REPORT_ANALYTICS, TRANSMIT_UFPD, TRANSMIT_PRECISE_GEO

    @JsonValue
    String getValue() {
        CaseUtils.toCamelCase(name(), false, '_' as char)
    }
}
