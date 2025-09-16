package org.prebid.server.proto.openrtb.ext.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import org.prebid.server.hooks.execution.model.ExecutionAction;
import org.prebid.server.hooks.execution.model.ExecutionStatus;
import org.prebid.server.hooks.execution.model.HookId;

import java.util.List;

@Builder
@Value
public class ExtModulesTraceInvocationResult {

    @JsonProperty("hookid")
    HookId hookId;

    @JsonProperty("executiontimemillis")
    Long executionTime;

    ExecutionStatus status;

    String message;

    ExecutionAction action;

    @JsonProperty("debugmessages")
    List<String> debugMessages;

    @JsonProperty("analyticstags")
    ExtModulesTraceAnalyticsTags analyticsTags;
}
