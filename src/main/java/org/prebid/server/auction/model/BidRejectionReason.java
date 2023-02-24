package org.prebid.server.auction.model;

import com.fasterxml.jackson.annotation.JsonValue;
import org.prebid.server.bidder.model.BidderError;

public enum BidRejectionReason {

    NO_BID(0),
    REJECTED_BY_HOOK(200),
    REJECTED_BY_MEDIA_TYPE(204),
    TIMED_OUT(101),
    REJECTED_DUE_TO_PRICE_FLOOR(301),
    FAILED_TO_REQUEST_BIDS(100),
    OTHER_ERROR(100);

    public final int code;

    BidRejectionReason(int code) {
        this.code = code;
    }

    @JsonValue
    private int getValue() {
        return code;
    }

    public static BidRejectionReason fromBidderError(BidderError error) {
        return switch (error.getType()) {
            case timeout -> BidRejectionReason.TIMED_OUT;
            case rejected_ipf -> BidRejectionReason.REJECTED_DUE_TO_PRICE_FLOOR;
            default -> BidRejectionReason.OTHER_ERROR;
        };
    }
}
