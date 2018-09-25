package org.prebid.server.metric;

public enum MetricName {
    // common
    active_connections,

    // database
    db_circuitbreaker_opened,
    db_circuitbreaker_closed,
    db_query_time,

    // http client
    httpclient_circuitbreaker_opened,
    httpclient_circuitbreaker_closed,

    // geo location
    geolocation_circuitbreaker_opened,
    geolocation_circuitbreaker_closed,

    // auction
    requests,
    app_requests,
    no_cookie_requests,
    safari_requests,
    safari_no_cookie_requests,
    request_time,
    prices,
    imps_requested,
    bids_received,
    adm_bids_received,
    nurl_bids_received,

    // request types,
    openrtb2web("openrtb2-web"),
    openrtb2app("openrtb2-app"),
    amp,
    legacy,

    // request and adapter statuses
    ok,
    nobid,
    gotbids,
    badinput,
    badserverresponse,
    failedtorequestbids,
    timeout,
    unknown_error,
    err,
    networkerr,

    // cookie sync
    cookie_sync_requests,
    opt_outs,
    bad_requests,
    sets,
    gdpr_prevent,
    gdpr_masked;

    private final String name;

    MetricName(String name) {
        this.name = name;
    }

    MetricName() {
        this.name = name();
    }

    @Override
    public String toString() {
        return name;
    }
}
