package org.prebid.server.hooks.execution.model;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.model.Endpoint;

import java.util.EnumMap;

@Builder
@Value
public class HookExecutionContext {

    Endpoint endpoint;

    EnumMap<Stage, StageExecutionOutcome> stageOutcomes;
}
