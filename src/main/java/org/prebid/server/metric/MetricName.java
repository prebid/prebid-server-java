package org.prebid.server.metric;

public enum MetricName {
    // auction
    requests,
    app_requests,
    no_cookie_requests,
    safari_requests,
    safari_no_cookie_requests,
    cookie_sync_requests,
    request_time,
    prices,
    imps_requested,
    bids_received,

    adm_bids_received,
    nurl_bids_received,

    // request types
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
    timeout,
    unknown_error,
    err,

    // cookie sync
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
