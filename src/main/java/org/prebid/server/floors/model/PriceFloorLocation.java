package org.prebid.server.floors.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum PriceFloorLocation {

    REQUEST, FETCH, NODATA;

    @Override
    @JsonValue
    public String toString() {
        return name().toLowerCase();
    }
}
