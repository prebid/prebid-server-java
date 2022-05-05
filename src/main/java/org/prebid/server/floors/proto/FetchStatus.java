package org.prebid.server.floors.proto;

import com.fasterxml.jackson.annotation.JsonValue;

public enum FetchStatus {

    SUCCESS, TIMEOUT, ERROR, INPROGRESS, NONE;

    @Override
    @JsonValue
    public String toString() {
        return name().toLowerCase();
    }
}
