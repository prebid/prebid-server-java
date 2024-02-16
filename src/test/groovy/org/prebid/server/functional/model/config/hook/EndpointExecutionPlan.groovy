package org.prebid.server.functional.model.config.hook

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class EndpointExecutionPlan {

    Map<Stage, StageExecutionPlan> stages

    static EndpointExecutionPlan getModuleEndpointExecutionPlan(ModuleName name, Stage stage) {
        new EndpointExecutionPlan(stages: [(stage): StageExecutionPlan.getModuleStageExecutionPlan(name, stage)])
    }
}
