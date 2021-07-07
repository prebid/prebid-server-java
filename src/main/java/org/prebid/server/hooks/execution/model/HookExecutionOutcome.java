package org.prebid.server.hooks.execution.model;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.hooks.v1.analytics.Tags;

import java.util.List;

@Builder
@Value
public class HookExecutionOutcome {

    HookId hookId;

    Long executionTime;

    ExecutionStatus status;

    String message;

    ExecutionAction action;

    List<String> errors;

    List<String> warnings;

    List<String> debugMessages;

    Tags analyticsTags;
}
