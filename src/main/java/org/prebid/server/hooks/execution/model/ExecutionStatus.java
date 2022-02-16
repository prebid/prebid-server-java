package org.prebid.server.hooks.execution.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ExecutionStatus {

    @JsonProperty("success")
    SUCCESS,
    @JsonProperty("failure")
    FAILURE,
    @JsonProperty("timeout")
    TIMEOUT,
    @JsonProperty("invocation_failure")
    INVOCATION_FAILURE,
    @JsonProperty("execution_failure")
    EXECUTION_FAILURE;
}
