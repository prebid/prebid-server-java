package org.prebid.server.metric;

public enum MetricName {
    // auction
    requests,
    app_requests,
    no_cookie_requests,
    no_bid_requests,
    timeout_requests,
    error_requests,
    invalid_requests,
    safari_requests,
    safari_no_cookie_requests,
    cookie_sync_requests,
    request_time,
    prices,
    imps_requested,
    bids_received,

    adm_bids_received,
    nurl_bids_received,

    ortb_requests,

    amp_requests,

    // cookie sync
    opt_outs,
    bad_requests,
    sets,
    gdpr_prevent,
    gdpr_masked
}
