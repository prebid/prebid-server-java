package org.prebid.server.exception;

public class PreBidException extends RuntimeException {

    public PreBidException(String message) {
        super(message);
    }

    public PreBidException(String message, Throwable cause) {
        super(message, cause);
    }
}
