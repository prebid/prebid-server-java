package org.prebid.server.cookie.exception;

import org.prebid.server.privacy.gdpr.model.TcfContext;

public class InvalidCookieSyncRequestException extends CookieSyncException {

    public InvalidCookieSyncRequestException(String message) {
        super(message, null);
    }

    public InvalidCookieSyncRequestException(String message, TcfContext tcfContext) {
        super(message, tcfContext);
    }
}
