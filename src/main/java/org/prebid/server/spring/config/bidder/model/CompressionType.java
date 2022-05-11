package org.prebid.server.spring.config.bidder.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum CompressionType {

    NONE,
    GZIP;

    @JsonValue
    public String getValue() {
        return toString().toLowerCase();
    }
}
