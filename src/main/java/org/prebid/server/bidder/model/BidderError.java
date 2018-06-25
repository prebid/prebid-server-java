package org.prebid.server.bidder.model;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Represents any kind of error produced by bidder.
 * Contains 'timedOut' flag to distinguish bidder calls failed due to timeout.
 */
@Value
@AllArgsConstructor(staticName = "of")
public class BidderError {

    public enum ErrorType {
        badInput, badServerResponse, timedOut
    }

    String message;

    ErrorType errorType;

    /**
     *
     */
    public static BidderError create(String message, ErrorType errorType) {
        return BidderError.of(message, errorType);
    }

    /**
     * Helper method to create bad input error
     */
    public static BidderError createBadInput(String message) {
        return BidderError.of(message, ErrorType.badInput);
    }

    /**
     * Helper method to create bad server response error
     */
    public static BidderError createBadServerResponse(String message) {
        return BidderError.of(message, ErrorType.badServerResponse);
    }

    /**
     * Helper method to create timed out error
     */
    public static BidderError createTimedOut(String message) {
        return BidderError.of(message, ErrorType.timedOut);
    }
}
