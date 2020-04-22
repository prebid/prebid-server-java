package org.prebid.server.metric;

public enum MetricName {
    // connection
    connection_accept_errors,

    // database
    db_circuitbreaker_opened,
    db_circuitbreaker_closed,
    db_query_time,

    // http client
    httpclient_circuitbreaker_opened,
    httpclient_circuitbreaker_closed,

    // geo location
    geolocation_requests,
    geolocation_successful,
    geolocation_fail,
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
    imps_banner,
    imps_video,
    imps_native,
    imps_audio,
    bids_received,
    adm_bids_received,
    nurl_bids_received,

    // request types,
    openrtb2web("openrtb2-web"),
    openrtb2app("openrtb2-app"),
    amp,
    video,
    legacy,

    // request and adapter statuses
    ok,
    nobid,
    gotbids,
    badinput,
    blacklisted_account,
    blacklisted_app,
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
    gen,
    matches,
    blocked,

    // tcf
    userid_removed,
    geo_masked,
    request_blocked,
    analytics_blocked,

    // stored data
    stored_requests_found,
    stored_requests_missing,
    stored_imps_found,
    stored_imps_missing,

    // cache
    prebid_cache_request_success_time,
    prebid_cache_request_error_time,

    //account.*.requests.
    rejected;

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
