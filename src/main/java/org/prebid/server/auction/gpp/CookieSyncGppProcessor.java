package org.prebid.server.auction.gpp;

import org.prebid.server.cookie.model.CookieSyncContext;
import org.prebid.server.proto.request.CookieSyncRequest;

public class CookieSyncGppProcessor {

    public CookieSyncRequest process(CookieSyncRequest cookieSyncRequest, CookieSyncContext cookieSyncContext) {
        return cookieSyncRequest;
    }
}
