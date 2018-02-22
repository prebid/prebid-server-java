package org.rtb.vexing.bidder.model;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Represents any kind of error produced by bidder.
 * Contains 'timedOut' flag to distinguish bidder calls failed due to timeout.
 */
@Value
@AllArgsConstructor(staticName = "of")
public class BidderError {

    String message;

    boolean timedOut;

    /**
     * Helper method to create non timeout errors.
     */
    public static BidderError create(String message) {
        return BidderError.of(message, false);
    }
}
