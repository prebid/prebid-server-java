package org.prebid.server.exception;

public class TargetingSyntaxException extends RuntimeException {

    public TargetingSyntaxException(String message) {
        super(message);
    }

    public TargetingSyntaxException(String message, Throwable cause) {
        super(message, cause);
    }
}
