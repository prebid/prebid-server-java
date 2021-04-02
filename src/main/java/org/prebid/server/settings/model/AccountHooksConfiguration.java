package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;
import org.prebid.server.hooks.execution.model.ExecutionPlan;

@Value(staticConstructor = "of")
public class AccountHooksConfiguration {

    @JsonProperty("execution-plan")
    ExecutionPlan executionPlan;
}
