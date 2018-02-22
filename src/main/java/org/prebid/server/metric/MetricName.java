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
    bids_received,

    open_rtb_requests,

    amp_requests,
    amp_no_cookie,

    // cookie sync
    opt_outs,
    bad_requests,
    sets
}
