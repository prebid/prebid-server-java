package org.prebid.server.hooks.execution.model;

import lombok.Value;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Value(staticConstructor = "of")
public class StageExecutionPlan {

    private static final StageExecutionPlan EMPTY = of(Collections.emptyList());

    List<ExecutionGroup> groups;

    public static StageExecutionPlan empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return Objects.equals(this, EMPTY);
    }
}
