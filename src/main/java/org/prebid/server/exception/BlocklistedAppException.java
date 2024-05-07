package org.prebid.server.exception;

public class BlocklistedAppException extends RuntimeException {

    public BlocklistedAppException(String message) {
        super(message);
    }
}
