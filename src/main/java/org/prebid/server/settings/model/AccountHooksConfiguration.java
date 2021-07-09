package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;
import org.prebid.server.hooks.execution.model.ExecutionPlan;

import java.util.Map;

@Value(staticConstructor = "of")
public class AccountHooksConfiguration {

    @JsonProperty("execution-plan")
    ExecutionPlan executionPlan;

    Map<String, ObjectNode> modules;
}
