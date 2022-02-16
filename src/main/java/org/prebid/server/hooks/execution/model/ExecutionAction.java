package org.prebid.server.hooks.execution.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ExecutionAction {

    @JsonProperty("no_action")
    NO_ACTION,
    @JsonProperty("update")
    UPDATE,
    @JsonProperty("reject")
    REJECT
}
