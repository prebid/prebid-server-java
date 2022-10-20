package org.prebid.server.exception;

import org.prebid.server.cookie.exception.CookieSyncException;

public class UnauthorizedUidsException extends CookieSyncException {

    public UnauthorizedUidsException(String message) {
        super(message, null);
    }
}
