package org.prebid.server.cookie.exception;

public class InvalidCookieSyncRequestException extends CookieSyncException {

    public InvalidCookieSyncRequestException(String message) {
        super(message, null);
    }
}
