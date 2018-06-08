package org.prebid.server.gdpr;

public class GdprException extends RuntimeException {

    public GdprException(String message) {
        super(message);
    }

    public GdprException(String message, Throwable cause) {
        super(message, cause);
    }
}
