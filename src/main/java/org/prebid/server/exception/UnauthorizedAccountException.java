package org.prebid.server.exception;

@SuppressWarnings("serial")
public class UnauthorizedAccountException extends RuntimeException {

    public UnauthorizedAccountException(String message) {
        super(message);
    }
}
