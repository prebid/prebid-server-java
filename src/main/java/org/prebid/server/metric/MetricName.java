package org.prebid.server.metric;

public enum MetricName {

    // connection
    CONNECTION_ACCEPT_ERRORS,

    // circuit breaker
    DB,
    GEO,
    HTTP,
    OPENED,
    EXISTING,

    // database
    DB_QUERY_TIME,

    // geo location
    GEOLOCATION_REQUESTS,
    GEOLOCATION_REQUEST_TIME,
    GEOLOCATION_SUCCESSFUL,
    GEOLOCATION_FAIL,

    // auction
    REQUESTS,
    APP_REQUESTS,
    NO_COOKIE_REQUESTS,
    REQUEST_TIME,
    PRICES,
    IMPS_REQUESTED,
    IMPS_BANNER,
    IMPS_VIDEO,
    IMPS_NATIVE,
    IMPS_AUDIO,
    BIDS_RECEIVED,
    ADM_BIDS_RECEIVED,
    NURL_BIDS_RECEIVED,

    // request types,
    OPENRTB2_WEB("openrtb2-web"),
    OPENRTB2_APP("openrtb2-app"),
    AMP,
    VIDEO,
    COOKIESYNC,
    SETUID,

    // event types
    EVENT_AUCTION("auction"),
    EVENT_AMP("amp"),
    EVENT_VIDEO("video"),
    EVENT_NOTIFICATION("event"),
    EVENT_COOKIE_SYNC("cookie_sync"),
    EVENT_SETUID("setuid"),
    EVENT_UNKNOWN("unknown"),

    // request and adapter statuses
    OK,
    FAILED,
    NOBID,
    GOTBIDS,
    BADINPUT,
    BLACKLISTED_ACCOUNT,
    BLACKLISTED_APP,
    BADSERVERRESPONSE,
    FAILEDTOREQUESTBIDS,
    TIMEOUT,
    BID_VALIDATION,
    UNKNOWN_ERROR,
    ERR,
    NETWORKERR,

    // bids validation
    WARN,

    // cookie sync
    COOKIE_SYNC_REQUESTS,
    OPT_OUTS,
    BAD_REQUESTS,
    SETS,
    GEN,
    MATCHES,
    BLOCKED,


    // tcf
    USERID_REMOVED,
    GEO_MASKED,
    REQUEST_BLOCKED,
    ANALYTICS_BLOCKED,

    // privacy
    COPPA,
    LMT,
    SPECIFIED,
    OPT_OUT("opt-out"),
    INVALID,
    IN_GEO("in-geo"),
    OUT_GEO("out-geo"),
    UNKNOWN_GEO("unknown-geo"),

    // vendor list
    MISSING,
    FALLBACK,

    // stored data
    STORED_REQUESTS_FOUND,
    STORED_REQUESTS_MISSING,
    STORED_IMPS_FOUND,
    STORED_IMPS_MISSING,

    // cache creative types
    JSON,
    XML,

    // account.*.requests.
    REJECTED_BY_INVALID_ACCOUNT("rejected.invalid-account"),
    REJECTED_BY_INVALID_STORED_IMPR("rejected.invalid-stored-impr"),
    REJECTED_BY_INVALID_STORED_REQUEST("rejected.invalid-stored-request"),

    // currency rates
    STALE,

    // settings cache
    STORED_REQUEST("stored-request"),
    AMP_STORED_REQUEST("amp-stored-request"),
    ACCOUNT,
    INITIALIZE,
    UPDATE,
    HIT,
    MISS,

    // hooks
    CALL,
    SUCCESS,
    NOOP,
    REJECT,
    UNKNOWN,
    FAILURE,
    EXECUTION_ERROR("execution-error"),
    DURATION,

    // price-floors
    PRICE_FLOORS("price-floors"),

    // win notifications
    WIN_NOTIFICATIONS,
    WIN_REQUESTS,
    WIN_REQUEST_PREPARATION_FAILED,
    WIN_REQUEST_TIME,
    WIN_REQUEST_FAILED,
    WIN_REQUEST_SUCCESSFUL,

    // user details
    USER_DETAILS_REQUESTS,
    USER_DETAILS_REQUEST_PREPARATION_FAILED,
    USER_DETAILS_REQUEST_TIME,
    USER_DETAILS_REQUEST_FAILED,
    USER_DETAILS_REQUEST_SUCCESSFUL,

    // pg
    PLANNER_LINEITEMS_RECEIVED,
    PLANNER_REQUESTS,
    PLANNER_REQUEST_FAILED,
    PLANNER_REQUEST_SUCCESSFUL,
    PLANNER_REQUEST_TIME,
    DELIVERY_REQUESTS,
    DELIVERY_REQUEST_FAILED,
    DELIVERY_REQUEST_SUCCESSFUL,
    DELIVERY_REQUEST_TIME;


    private final String name;

    MetricName(String name) {
        this.name = name;
    }

    MetricName() {
        this.name = name();
    }

    @Override
    public String toString() {
        return name.toLowerCase();
    }
}
