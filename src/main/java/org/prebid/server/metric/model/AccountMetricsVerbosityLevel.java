package org.prebid.server.metric.model;

public enum AccountMetricsVerbosityLevel {

    none, basic, detailed;

    public boolean isAtLeast(AccountMetricsVerbosityLevel another) {
        return this.ordinal() >= another.ordinal();
    }
}
