package org.prebid.server.bidder.model;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Represents any kind of error produced by bidder.
 */
@Value
@AllArgsConstructor(staticName = "of")
public class BidderError {

    String message;

    Type type;

    public static BidderError create(String message, Type type) {
        return BidderError.of(message, type);
    }

    public static BidderError generic(String message) {
        return BidderError.of(message, Type.GENERIC);
    }

    public static BidderError invalidBid(String message) {
        return BidderError.of(message, Type.INVALID_BID);
    }

    public static BidderError badInput(String message) {
        return BidderError.of(message, Type.BAD_INPUT);
    }

    public static BidderError badServerResponse(String message) {
        return BidderError.of(message, Type.BAD_SERVER_RESPONSE);
    }

    public static BidderError rejectedIpf(String message) {
        return BidderError.of(message, Type.REJECTED_IPF);
    }

    public static BidderError failedToRequestBids(String message) {
        return BidderError.of(message, Type.FAILED_TO_REQUEST_BIDS);
    }

    public static BidderError timeout(String message) {
        return BidderError.of(message, Type.TIMEOUT);
    }

    public enum Type {
        /**
         * Should be used when returning errors which are caused by bad input.
         * It should _not_ be used if the error is a server-side issue (e.g. failed to send the external request).
         * Error of this type will not be written to the app log, since it's not an actionable item for the Prebid
         * Server hosts.
         */
        BAD_INPUT(2),

        /**
         * Should be used when returning errors which are caused by bad/unexpected behavior on the remote server.
         * <p>
         * For example:
         * <p>
         * - The external server responded with a 500
         * - The external server gave a malformed or unexpected response.
         * <p>
         * These should not be used to log _connection_ errors (e.g. "couldn't find host"), which may indicate config
         * issues for the PBS host company
         */
        BAD_SERVER_RESPONSE(3),

        /**
         * Covers the case where a bidder failed to generate any http requests to get bids, but did not generate any
         * error messages. This should not happen in practice and will signal that an bidder is poorly coded.
         * If there was something wrong with a request such that an bidder could not generate a bid, then it should
         * generate an error explaining the deficiency. Otherwise it will be extremely difficult to debug the reason
         * why a bidder is not bidding.
         */
        FAILED_TO_REQUEST_BIDS(4),

        /**
         * Covers the case where a bid does not pass validation with error or warnings. One instance per invalid bid
         * created with aggregation for all warnings and errors.
         */
        INVALID_BID(5),

        /**
         * Covers the case where a bid was rejected by price-floors feature functionality
         */
        REJECTED_IPF(6),

        TIMEOUT(1),
        GENERIC(999);

        private final Integer code;

        Type(final Integer errorCode) {
            this.code = errorCode;
        }

        public Integer getCode() {
            return code;
        }
    }
}
