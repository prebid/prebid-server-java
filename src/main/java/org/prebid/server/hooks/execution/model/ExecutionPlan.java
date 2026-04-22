package org.prebid.server.hooks.execution.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Value;
import org.prebid.server.json.deserializer.ExecutionPlanEndpointsConfigDeserializer;

import java.util.List;
import java.util.Map;

@Value(staticConstructor = "of")
public class ExecutionPlan {

    private static final ExecutionPlan EMPTY = of(null, null);

    @JsonProperty("abtests")
    List<ABTest> abTests;

    @JsonDeserialize(using = ExecutionPlanEndpointsConfigDeserializer.class)
    Map<HookHttpEndpoint, EndpointExecutionPlan> endpoints;

    public static ExecutionPlan empty() {
        return EMPTY;
    }
}
