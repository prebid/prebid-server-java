package org.prebid.server.hooks.execution.model;

import lombok.Value;
import org.prebid.server.model.Endpoint;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Value(staticConstructor = "of")
public class HookExecutionContext {

    Endpoint endpoint;

    EnumMap<Stage, List<StageExecutionOutcome>> stageOutcomes;

    Map<String, Object> moduleContexts = new HashMap<>();

    public static HookExecutionContext of(Endpoint endpoint) {
        return of(endpoint, new EnumMap<>(Stage.class));
    }
}
