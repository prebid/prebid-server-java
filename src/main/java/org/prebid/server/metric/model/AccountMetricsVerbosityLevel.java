package org.prebid.server.metric.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum AccountMetricsVerbosityLevel {

    NONE,
    BASIC,
    DETAILED;

    public boolean isAtLeast(AccountMetricsVerbosityLevel another) {
        return this.ordinal() >= another.ordinal();
    }

    @Override
    @JsonValue
    public String toString() {
        return name().toLowerCase();
    }
}
