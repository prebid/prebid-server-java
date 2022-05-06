package org.prebid.server.floors.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum PriceFloorLocation {

    REQUEST("request"),
    FETCH("fetch"),
    NO_DATA("noData");

    private final String value;

    PriceFloorLocation(String type) {
        this.value = type;
    }

    @Override
    @JsonValue
    public String toString() {
        return getValue();
    }

    private String getValue() {
        return value;
    }
}
