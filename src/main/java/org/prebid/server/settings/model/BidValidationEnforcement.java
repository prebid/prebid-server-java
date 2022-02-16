package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum BidValidationEnforcement {

    @JsonProperty("skip")
    SKIP,
    @JsonProperty("enforce")
    ENFORCE,
    @JsonProperty("warn")
    WARN;

    @Override
    public String toString() {
        return super.toString().toLowerCase();
    }
}
