package org.prebid.server.bidder.visiblemeasures.model;

public enum VisibleMeasuresType {

    PUBLISHER("publisher"),
    NETWORK("network");

    private final String value;

    VisibleMeasuresType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
