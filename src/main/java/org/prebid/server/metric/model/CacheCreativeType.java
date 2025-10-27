package org.prebid.server.metric.model;

public enum CacheCreativeType {

    ENTRY("entry"),
    CREATIVE("creative");

    private final String type;

    CacheCreativeType(String type) {
        this.type = type;
    }

    public String getType() {
        return this.type;
    }
}
