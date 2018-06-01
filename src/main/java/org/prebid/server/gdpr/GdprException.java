package org.prebid.server.gdpr;

class GdprException extends RuntimeException {

    GdprException(String message) {
        super(message);
    }

    GdprException(String message, Throwable cause) {
        super(message, cause);
    }
}
