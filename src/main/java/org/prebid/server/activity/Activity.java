package org.prebid.server.activity;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public enum Activity {

    @JsonProperty("sync_user")
    @JsonAlias({"syncUser", "sync-user"})
    SYNC_USER,

    @JsonProperty("fetch_bids")
    @JsonAlias({"fetchBids", "fetch-bids"})
    CALL_BIDDER,

    @JsonProperty("enrich_ufpd")
    @JsonAlias({"enrichUfpd", "enrich-ufpd"})
    MODIFY_UFDP,

    @JsonProperty("transmit_ufpd")
    @JsonAlias({"transmitUfpd", "transmit-ufpd"})
    TRANSMIT_UFPD,

    @JsonProperty("transmit_eids")
    @JsonAlias({"transmitEids", "transmit-eids"})
    TRANSMIT_EIDS,

    @JsonProperty("transmit_precise_geo")
    @JsonAlias({"transmitPreciseGeo", "transmit-precise-geo"})
    TRANSMIT_GEO,

    @JsonProperty("transmit_tid")
    @JsonAlias({"transmitTid", "transmit-tid"})
    TRANSMIT_TID,

    @JsonProperty("report_analytics")
    @JsonAlias({"reportAnalytics", "report-analytics"})
    REPORT_ANALYTICS
}
