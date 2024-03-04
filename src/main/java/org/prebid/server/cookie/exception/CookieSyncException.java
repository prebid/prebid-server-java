package org.prebid.server.cookie.exception;

import org.prebid.server.privacy.gdpr.model.TcfContext;

public class CookieSyncException extends RuntimeException {

    public final TcfContext tcfContext;

    public CookieSyncException(Throwable error, TcfContext tcfContext) {
        super(error);
        this.tcfContext = tcfContext;
    }

    public CookieSyncException(String error, TcfContext tcfContext) {
        super(error);
        this.tcfContext = tcfContext;
    }
}
