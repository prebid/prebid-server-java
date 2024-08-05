package org.prebid.server.metric;

public enum MetricName {

    general,

    // connection
    connection_accept_errors,

    // circuit breaker
    db,
    geo,
    http,
    opened,
    existing,

    // database
    db_query_time,

    // geo location
    geolocation_requests,
    geolocation_request_time,
    geolocation_successful,
    geolocation_fail,

    // auction
    requests,
    app_requests,
    no_cookie_requests,
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
    openrtb2dooh("openrtb2-dooh"),
    amp,
    video,
    cookiesync,
    setuid,

    // event types
    event_auction("auction"),
    event_amp("amp"),
    event_video("video"),
    event_notification("event"),
    event_cookie_sync("cookie_sync"),
    event_setuid("setuid"),
    event_unknown("unknown"),

    // request and adapter statuses
    ok,
    failed,
    nobid,
    gotbids,
    badinput,
    blocklisted_account,
    blocklisted_app,
    badserverresponse,
    failedtorequestbids,
    timeout,
    bid_validation,
    unknown_error,
    err,
    networkerr,

    // bids validation
    warn,

    // cookie sync
    cookie_sync_requests,
    opt_outs,
    bad_requests,
    sets,
    blocked,
    filtered,
    sizeblocked,
    sizedout,

    // tcf
    userfpd_masked,
    userid_removed,
    geo_masked,
    request_blocked,
    analytics_blocked,

    // privacy
    coppa,
    lmt,
    specified,
    opt_out("opt-out"),
    invalid,
    in_geo("in-geo"),
    out_geo("out-geo"),
    unknown_geo("unknown-geo"),

    // vendor list
    missing,
    fallback,

    // stored data
    stored_requests_found,
    stored_requests_missing,
    stored_imps_found,
    stored_imps_missing,

    // cache creative types
    json,
    xml,

    // account.*.requests.
    rejected_by_invalid_account("rejected.invalid-account"),
    rejected_by_invalid_stored_impr("rejected.invalid-stored-impr"),
    rejected_by_invalid_stored_request("rejected.invalid-stored-request"),
    rejected_by_account_fetch_failed("rejected.account-fetch-failed"),

    // currency rates
    stale,

    // settings cache
    stored_request("stored-request"),
    amp_stored_request("amp-stored-request"),
    account,
    initialize,
    update,
    hit,
    miss,

    // hooks
    call,
    success,
    noop,
    reject,
    unknown,
    failure,
    execution_error("execution-error"),
    duration,

    // price-floors
    price_floors("price-floors"),

    // activity
    disallowed_count("disallowed.count"),
    processed_rules_count("processedrules.count");

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
