package org.prebid.server.hooks.execution.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class HookExecutionOutcome {

    String moduleCode;

    String hookCode;

    Long executionTime;

    ExecutionStatus status;

    String message;

    ExecutionAction action;

    List<String> errors;

    List<String> warnings;

    List<String> debugMessages;
}
