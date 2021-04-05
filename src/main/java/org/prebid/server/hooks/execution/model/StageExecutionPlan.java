package org.prebid.server.hooks.execution.model;

import lombok.Value;

import java.util.Collections;
import java.util.List;

@Value(staticConstructor = "of")
public class StageExecutionPlan {

    private static final StageExecutionPlan EMPTY = of(Collections.emptyList());

    List<ExecutionGroup> groups;

    public static StageExecutionPlan empty() {
        return EMPTY;
    }
}
