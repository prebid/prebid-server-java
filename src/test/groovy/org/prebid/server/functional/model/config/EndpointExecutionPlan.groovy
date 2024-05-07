package org.prebid.server.functional.model.config

import groovy.transform.ToString
import org.prebid.server.functional.model.ModuleName

@ToString(includeNames = true, ignoreNulls = true)
class EndpointExecutionPlan {

    Map<Stage, StageExecutionPlan> stages

    static EndpointExecutionPlan getModuleEndpointExecutionPlan(ModuleName name, Stage stage) {
        new EndpointExecutionPlan(stages: [(stage): StageExecutionPlan.getModuleStageExecutionPlan(name, stage)])
    }
}
