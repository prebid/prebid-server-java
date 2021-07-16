package org.prebid.server.hooks.execution.model;

import lombok.Value;
import org.prebid.server.model.Endpoint;

import java.util.Collections;
import java.util.Map;

@Value(staticConstructor = "of")
public class ExecutionPlan {

    private static final ExecutionPlan EMPTY = of(Collections.emptyMap());

    Map<Endpoint, EndpointExecutionPlan> endpoints;

    public static ExecutionPlan empty() {
        return EMPTY;
    }
}
