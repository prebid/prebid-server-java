package org.prebid.server.metric.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum AccountMetricsVerbosityLevel {

    @JsonProperty("none")
    NONE,
    @JsonProperty("basic")
    BASIC,
    @JsonProperty("detailed")
    DETAILED;

    public boolean isAtLeast(AccountMetricsVerbosityLevel another) {
        return this.ordinal() >= another.ordinal();
    }
}
