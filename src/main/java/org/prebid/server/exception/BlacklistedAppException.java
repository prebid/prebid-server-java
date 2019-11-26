package org.prebid.server.exception;

public class BlacklistedAppException extends RuntimeException {

    public BlacklistedAppException(String message) {
        super(message);
    }
}
