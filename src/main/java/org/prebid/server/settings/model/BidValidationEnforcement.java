package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum BidValidationEnforcement {

    SKIP,
    ENFORCE,
    WARN;

    @Override
    @JsonValue
    public String toString() {
        return super.toString().toLowerCase();
    }
}
