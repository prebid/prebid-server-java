package org.prebid.server.cookie.exception;

import org.prebid.server.privacy.gdpr.model.TcfContext;

public class UnauthorizedUidsException extends CookieSyncException {

    public UnauthorizedUidsException(String message, TcfContext tcfContext) {
        super(message, tcfContext);
    }
}
