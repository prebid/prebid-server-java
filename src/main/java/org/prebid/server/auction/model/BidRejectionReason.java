package org.prebid.server.auction.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The list of the Seat Non Bid codes:
 * 0 - the bidder is called but declines to bid and doesn't provide a code (for the impression)
 * 100-199 - the bidder is called but returned with an unspecified error (for the impression)
 * 200-299 - the bidder is not called at all
 * 300-399 - the bidder is called, but its response is rejected
 */
public enum BidRejectionReason {

    /**
     * If the bidder returns in time but declines to bid and doesn’t provide an “NBR” code.
     */
    NO_BID(0),

    /**
     * The bidder returned with an unspecified error for this impression.
     * Applied if any other ERROR is not recognized.
     */
    ERROR_GENERAL(100),

    /**
     * The bidder failed because of timeout
     */
    ERROR_TIMED_OUT(101),

    /**
     * The bidder returned status code less than 200 OR greater than or equal to 400
     */
    ERROR_INVALID_BID_RESPONSE(102),

    /**
     * The bidder returned HTTP 503
     */
    ERROR_BIDDER_UNREACHABLE(103),

    /**
     * The bidder is not called at all.
     * Applied if any other REQUEST_BLOCKED reason is not recognized.
     */
    REQUEST_BLOCKED_GENERAL(200),

    /**
     * If the request was not sent to the bidder because they don’t support dooh or app
     */
    REQUEST_BLOCKED_UNSUPPORTED_CHANNEL(201),

    /**
     * This impression not sent to the bid adapter because it doesn’t support the requested mediatype.
     */
    REQUEST_BLOCKED_UNSUPPORTED_MEDIA_TYPE(202),

    /**
     * If the bidder was not called due to GDPR purpose 2
     */
    REQUEST_BLOCKED_PRIVACY(204),

    /**
     * If the bidder was not called due to a mismatch between the bidder’s currency and the request’s currency.
     */
    REQUEST_BLOCKED_UNACCEPTABLE_CURRENCY(205),

    /**
     * The bidder is called, but its response is rejected.
     * Applied if any other RESPONSE_REJECTED reason is not recognized.
     */
    RESPONSE_REJECTED_GENERAL(300),

    /**
     * The bidder returns a bid that doesn't meet the price floor.
     */
    RESPONSE_REJECTED_BELOW_FLOOR(301),

    /**
     * The bidder returns a bid that has been rejected as a duplicate.
     */
    RESPONSE_REJECTED_DUPLICATE(302),

    /**
     * The bidder returns a bid that doesn't meet the price deal floor.
     */
    RESPONSE_REJECTED_BELOW_DEAL_FLOOR(304),

    /**
     * Rejected by the DSA validations
     */
    RESPONSE_REJECTED_DSA_PRIVACY(305),

    /**
     * If the ortbblocking module enforced a bid response for battr, bcat, bapp, btype.
     * If the richmedia module filtered out a bid response.
     */
    RESPONSE_REJECTED_INVALID_CREATIVE(350),

    /**
     * If a bid response was rejected due to size.
     * When the auction.bid-validations.banner-creative-max-size is in enforce mode and rejects a bid.
     */
    RESPONSE_REJECTED_INVALID_CREATIVE_SIZE_NOT_ALLOWED(351),

    /**
     * If a bid response was rejected due to auction.validations.secure-markup
     */
    RESPONSE_REJECTED_INVALID_CREATIVE_NOT_SECURE(352),

    /**
     * If the ortbblocking module enforced a bid response due to badv
     */
    RESPONSE_REJECTED_ADVERTISER_BLOCKED(356);

    private final int code;

    BidRejectionReason(int code) {
        this.code = code;
    }

    @JsonValue
    public int getValue() {
        return code;
    }

}
