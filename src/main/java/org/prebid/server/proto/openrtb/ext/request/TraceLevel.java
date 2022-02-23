package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonValue;

public enum TraceLevel {

    BASIC,
    VERBOSE;

    @Override
    @JsonValue
    public String toString() {
        return name().toLowerCase();
    }
}
