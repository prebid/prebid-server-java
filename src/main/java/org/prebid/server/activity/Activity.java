package org.prebid.server.activity;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum Activity {

    @JsonProperty("syncUser")
    SYNC_USER,

    @JsonProperty("fetchBids")
    CALL_BIDDER,

    @JsonProperty("enrichUfpd")
    MODIFY_UFDP,

    @JsonProperty("transmitUfpd")
    TRANSMIT_UFPD,

    @JsonProperty("transmitPreciseGeo")
    TRANSMIT_GEO,

    @JsonProperty("reportAnalytics")
    REPORT_ANALYTICS
}
