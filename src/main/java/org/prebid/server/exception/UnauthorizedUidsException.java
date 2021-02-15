package org.prebid.server.exception;

@SuppressWarnings("serial")
public class UnauthorizedUidsException extends RuntimeException {

    public UnauthorizedUidsException(String message) {
        super(message);
    }
}
