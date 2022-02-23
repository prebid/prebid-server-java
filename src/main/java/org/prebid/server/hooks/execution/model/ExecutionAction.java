package org.prebid.server.hooks.execution.model;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ExecutionAction {

    NO_ACTION,
    UPDATE,
    REJECT;

    @Override
    @JsonValue
    public String toString() {
        return name().toLowerCase();
    }
}
