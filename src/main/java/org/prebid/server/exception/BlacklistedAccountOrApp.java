package org.prebid.server.exception;

public class BlacklistedAccountOrApp extends RuntimeException {

    public BlacklistedAccountOrApp(String message) {
        super(message);
    }
}
