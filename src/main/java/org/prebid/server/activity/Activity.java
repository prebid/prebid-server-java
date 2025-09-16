package org.prebid.server.activity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public enum Activity {

    @JsonProperty("syncUser")
    @JsonAlias({"sync_user", "sync-user"})
    SYNC_USER,

    @JsonProperty("fetchBids")
    @JsonAlias({"fetch_bids", "fetch-bids"})
    CALL_BIDDER,

    @JsonProperty("enrichUfpd")
    @JsonAlias({"enrich_ufpd", "enrich-ufpd"})
    MODIFY_UFDP,

    @JsonProperty("transmitUfpd")
    @JsonAlias({"transmit_ufpd", "transmit-ufpd"})
    TRANSMIT_UFPD,

    @JsonProperty("transmitEids")
    @JsonAlias({"transmit_eids", "transmit-eids"})
    TRANSMIT_EIDS,

    @JsonProperty("transmitPreciseGeo")
    @JsonAlias({"transmit_precise_geo", "transmit-precise-geo"})
    TRANSMIT_GEO,

    @JsonProperty("transmitTid")
    @JsonAlias({"transmit_tid", "transmit-tid"})
    TRANSMIT_TID,

    @JsonProperty("reportAnalytics")
    @JsonAlias({"report_analytics", "report-analytics"})
    REPORT_ANALYTICS
}
