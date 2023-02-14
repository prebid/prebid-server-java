package org.prebid.server.auction.gpp;

import org.prebid.server.cookie.model.CookieSyncContext;
import org.prebid.server.proto.request.CookieSyncRequest;

public class CookieSyncGppService {

    private final GppService gppService;

    public CookieSyncGppService(GppService gppService) {
        this.gppService = gppService;
    }

    public CookieSyncRequest apply(CookieSyncRequest cookieSyncRequest, CookieSyncContext cookieSyncContext) {
        return cookieSyncRequest;
    }
}
