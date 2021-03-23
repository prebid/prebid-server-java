package org.prebid.server.hooks.execution.model;

import lombok.Builder;
import lombok.Value;

import java.util.EnumMap;

@Builder
@Value
public class HookExecutionContext {

    EnumMap<Stage, StageExecutionOutcome> stageOutcomes;
}
