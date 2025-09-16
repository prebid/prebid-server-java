package org.prebid.server.exception;

@SuppressWarnings("serial")
public class PreBidException extends RuntimeException {

    public PreBidException(String message) {
        super(message);
    }

    public PreBidException(String message, Throwable cause) {
        super(message, cause);
    }
}
