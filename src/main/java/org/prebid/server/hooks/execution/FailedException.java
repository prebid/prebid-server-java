package org.prebid.server.hooks.execution;

class FailedException extends RuntimeException {

    FailedException(String message) {
        super(message);
    }

    FailedException(Throwable cause) {
        super(cause);
    }
}
