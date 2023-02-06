package org.prebid.server.cookie.model;

public enum RejectionReason {

    INVALID_BIDDER,
    DISABLED_BIDDER,
    REJECTED_BY_TCF,
    REJECTED_BY_CCPA,
    UNCONFIGURED_USERSYNC,
    DISABLED_USERSYNC,
    REJECTED_BY_FILTER,
    ALREADY_IN_SYNC
}
