package org.prebid.server.auction.model;

public enum RejectionReason {

    UNKNOWN(0);

    public final int code;

    RejectionReason(int code) {
        this.code = code;
    }
}
