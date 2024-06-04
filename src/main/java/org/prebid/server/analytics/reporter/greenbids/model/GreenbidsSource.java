package org.prebid.server.analytics.reporter.greenbids.model;

public enum GreenbidsSource {
    GPID_SOURCE("gpidSource"),
    STORED_REQUEST_ID_SOURCE("storedRequestIdSource"),
    AD_UNIT_CODE_SOURCE("adUnitCodeSource");

    private final String value;

    GreenbidsSource(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
