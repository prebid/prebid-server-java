package org.prebid.server.exception;

public class PreBidException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PreBidException(String message) {
        super(message);
    }

    public PreBidException(String message, Throwable cause) {
        super(message, cause);
    }
}
