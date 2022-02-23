package org.prebid.server.hooks.execution.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ExecutionStatus {

    SUCCESS,
    FAILURE,
    TIMEOUT,
    INVOCATION_FAILURE,
    EXECUTION_FAILURE;

    @Override
    @JsonValue
    public String toString() {
        return name().toLowerCase();
    }
}
