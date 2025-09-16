package org.prebid.server.hooks.execution.model;

import lombok.Value;

import java.util.Collections;
import java.util.Map;

@Value(staticConstructor = "of")
public class EndpointExecutionPlan {

    private static final EndpointExecutionPlan EMPTY = of(Collections.emptyMap());

    Map<Stage, StageExecutionPlan> stages;

    public static EndpointExecutionPlan empty() {
        return EMPTY;
    }
}
