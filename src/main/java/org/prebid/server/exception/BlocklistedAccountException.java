package org.prebid.server.exception;

public class BlocklistedAccountException extends RuntimeException {

    public BlocklistedAccountException(String message) {
        super(message);
    }
}
