package org.prebid.server.metric;

public enum MetricName {

    alerts_account_config("alerts.account_config"),
    analytics_events("analytics.events"),
    price_floors_general_err("price-floors.general.err"),
    price_floors_fetch_failure("price-floors.fetch.failure"),
    response_validation("response.validation"),
    adapter_cookie_sync_action("adapter.cookie_sync.action"),
    adapter_cookie_sync_tcf("adapter.cookie_sync.tcf"),
    adapter_user_sync_action("adapter.user_sync.action"),
    adapter_user_sync_tcf("adapter.user_sync.tcf"),
    user_sync_bad_requests("user_sync.bad_requests"),
    user_sync_opt_outs("user_sync.opt_outs"),
    adapter_tcf("adapter.tcf"),


    // privacy
    privacy_tcf_errors("privacy.tcf.errors"),
    privacy_tcf_requests("privacy.tcf.requests"),
    privacy_tcf_unknown_geo("privacy.tcf.unknown-geo"),
    privacy_tcf_in_geo("privacy.tcf.in-geo"),
    privacy_tcf_out_geo("privacy.tcf.out-geo"),
    privacy_tcf_vendorlist("privacy.tcf.vendorlist"),
    privacy_usp_specified("privacy.usp.specified"),
    privacy_usp_opt_out("privacy.usp.opt-out"),
    privacy_coopa("privacy.coopa"),
    privacy_lmt("privacy.lmt"),

    // connection
    connection_accept_errors,

    // circuit breaker
    cb_db_open("circuit-breaker.db.opened"),
    cb_geo_open("circuit-breaker.geo.opened"),
    cb_http_open("circuit-breaker.http.named.opened"),
    cb_http_existing("circuit.breaker.http.existing"),

    // pg
    pg_planner_lineitems_received("pg.planner_lineitems_received"),
    pg_planner_requests("pg.planner_requests"),
    pg_planner_request_failed("pg.planner_request_failed"),
    pg_planner_request_successful("pg.planner_request_successful"),
    pg_planner_request_time("pg.planner_request_time"),
    pg_delivery_requests("pg.delivery_requests"),
    pg_delivery_request_failed("pg.delivery_request_failed"),
    pg_delivery_request_successful("pg.delivery_request_successful"),
    pg_delivery_request_time("pg.delivery_request_time"),

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
    blacklisted_account,
    blacklisted_app,
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
    gen,
    matches,
    blocked,

    // tcf
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

    // win notifications
    win_notifications,
    win_requests,
    win_request_preparation_failed,
    win_request_time,
    win_request_failed,
    win_request_successful,

    // user details
    user_details_requests,
    user_details_request_preparation_failed,
    user_details_request_time,
    user_details_request_failed,
    user_details_request_successful,

    adapter_requests_result("requests.result"),

    bidder_cardinality_requests("bidder-cardinality.requests"),

    // hooks
    account_module_calls("account.module_calls"),
    module_calls("module.calls"),

    // settings
    settings_cache_refresh_db_query_time("settings.cache.refresh.db_query_time"),
    settings_cache_refresh_err("settings.cache.refresh.err"),
    settings_cache_account("settings.cache.account"),

    // currency
    currency_rates_stale("currency-rates.stale"),

    // timeout notifications
    timeout_notifications("timeout_notifications"),

    // cache
    prebid_cache_requests("prebid_cache.requests"),
    prebid_cache_creative_size("prebid_cache.creative_size");

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
