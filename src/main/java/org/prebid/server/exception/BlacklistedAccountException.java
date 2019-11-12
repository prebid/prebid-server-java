package org.prebid.server.exception;

public class BlacklistedAccountException extends RuntimeException {

    public BlacklistedAccountException(String message) {
        super(message);
    }
}
