package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum AccountStatus {

    ACTIVE,
    INACTIVE;

    @Override
    @JsonValue
    public String toString() {
        return name().toLowerCase();
    }
}
