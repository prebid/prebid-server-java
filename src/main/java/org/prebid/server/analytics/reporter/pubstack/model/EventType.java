package org.prebid.server.analytics.reporter.pubstack.model;

public enum EventType {

    AMP, AUCTION, COOKIESYNC, NOTIFICATION, SETUID, VIDEO;

    @Override
    public String toString() {
        return super.toString().toLowerCase();
    }
}

