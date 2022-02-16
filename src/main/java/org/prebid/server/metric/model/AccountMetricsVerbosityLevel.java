package org.prebid.server.metric.model;

public enum AccountMetricsVerbosityLevel {

    NONE, BASIC, DETAILED;

    public boolean isAtLeast(AccountMetricsVerbosityLevel another) {
        return this.ordinal() >= another.ordinal();
    }
}
