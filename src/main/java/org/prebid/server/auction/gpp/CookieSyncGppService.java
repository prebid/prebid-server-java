package org.prebid.server.auction.gpp;

import org.prebid.server.cookie.model.CookieSyncContext;
import org.prebid.server.proto.request.CookieSyncRequest;

public class CookieSyncGppService {

    public CookieSyncRequest apply(CookieSyncRequest cookieSyncRequest, CookieSyncContext cookieSyncContext) {
        return cookieSyncRequest;
    }
}
