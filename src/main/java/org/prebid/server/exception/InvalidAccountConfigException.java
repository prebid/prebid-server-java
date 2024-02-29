package org.prebid.server.exception;

public class InvalidAccountConfigException extends RuntimeException {

    public InvalidAccountConfigException(String message) {
        super(message);
    }
}
