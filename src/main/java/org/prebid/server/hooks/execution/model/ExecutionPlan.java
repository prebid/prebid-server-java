package org.prebid.server.hooks.execution.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;
import org.prebid.server.model.Endpoint;

import java.util.List;
import java.util.Map;

@Value(staticConstructor = "of")
public class ExecutionPlan {

    private static final ExecutionPlan EMPTY = of(null, null);

    @JsonProperty("abtests")
    List<ABTest> abTests;

    Map<Endpoint, EndpointExecutionPlan> endpoints;

    public static ExecutionPlan empty() {
        return EMPTY;
    }
}
