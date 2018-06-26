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
        return BidderError.of(message, Type.generic);
    }

    public static BidderError badInput(String message) {
        return BidderError.of(message, Type.bad_input);
    }

    public static BidderError badServerResponse(String message) {
        return BidderError.of(message, Type.bad_server_response);
    }

    public static BidderError timeout(String message) {
        return BidderError.of(message, Type.timeout);
    }

    public enum Type {
        /**
         * Should be used when returning errors which are caused by bad input.
         * It should _not_ be used if the error is a server-side issue (e.g. failed to send the external request).
         * Error of this type will not be written to the app log, since it's not an actionable item for the Prebid
         * Server hosts.
         */
        bad_input,

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
        bad_server_response,

        timeout,
        generic
    }
}
