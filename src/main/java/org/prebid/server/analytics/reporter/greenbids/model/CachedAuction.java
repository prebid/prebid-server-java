package org.prebid.server.analytics.reporter.greenbids.model;

import java.util.ArrayList;

public class CachedAuction {
    ArrayList<Object> timeoutBids;
    String greenbidsId;
    String billingId;
    Boolean isSampled;

    public CachedAuction() {
        this.timeoutBids = new ArrayList<>();
        this.greenbidsId = null;
        this.billingId = null;
        this.isSampled = true;
    }
}

