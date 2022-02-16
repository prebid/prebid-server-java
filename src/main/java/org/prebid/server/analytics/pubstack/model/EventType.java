package org.prebid.server.analytics.pubstack.model;

public enum EventType {

    AUCTION, COOKIESYNC, AMP, SETUID, VIDEO;

    @Override
    public String toString() {
        return super.toString().toLowerCase();
    }
}

