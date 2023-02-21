package org.prebid.server.auction.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum BidRejectionReason {

    NO_BID(0),
    REJECTED_BY_HOOK(-1),
    REJECTED_BY_MEDIA_TYPE(-2),
    TIMED_OUT(101),
    REJECTED_DUE_TO_PRICE_FLOOR(301),
    FAILED_TO_REQUEST_BIDS(-6),
    OTHER_ERROR(100);

    public final int code;

    BidRejectionReason(int code) {
        this.code = code;
    }

    @JsonValue
    private int getValue() {
        return code;
    }
}
