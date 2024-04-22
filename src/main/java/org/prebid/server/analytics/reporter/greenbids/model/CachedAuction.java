package org.prebid.server.analytics.reporter.greenbids.model;

import java.util.ArrayList;

public class CachedAuction {
    public ArrayList<Object> timeoutBids;
    public String greenbidsId;
    public String billingId;
    public Boolean isSampled;

    public CachedAuction() {
        this.timeoutBids = new ArrayList<>();
        this.greenbidsId = null;
        this.billingId = null;
        this.isSampled = true;
    }
}

