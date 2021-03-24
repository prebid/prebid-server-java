package org.prebid.server.hooks.execution.model;

import lombok.Value;
import org.prebid.server.model.Endpoint;

import java.util.EnumMap;

@Value(staticConstructor = "of")
public class HookExecutionContext {

    Endpoint endpoint;

    EnumMap<Stage, StageExecutionOutcome> stageOutcomes;

    public static HookExecutionContext of(Endpoint endpoint) {
        return of(endpoint, new EnumMap<>(Stage.class));
    }
}
