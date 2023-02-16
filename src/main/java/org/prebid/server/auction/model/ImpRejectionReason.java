package org.prebid.server.auction.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ImpRejectionReason {

    NO_BID(0);

    public final int code;

    ImpRejectionReason(int code) {
        this.code = code;
    }

    @JsonValue
    private int getValue() {
        return code;
    }
}
