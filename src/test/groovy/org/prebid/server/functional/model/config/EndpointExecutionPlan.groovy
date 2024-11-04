package org.prebid.server.functional.model.config

import groovy.transform.ToString
import org.prebid.server.functional.model.ModuleName

@ToString(includeNames = true, ignoreNulls = true)
class EndpointExecutionPlan {

    Map<Stage, StageExecutionPlan> stages

    static EndpointExecutionPlan getModuleEndpointExecutionPlan(ModuleName name, List<Stage> stages) {
        new EndpointExecutionPlan(stages:  stages.collectEntries {
            it -> [(it): StageExecutionPlan.getModuleStageExecutionPlan(name, it)] } as Map<Stage, StageExecutionPlan>)
    }

    static EndpointExecutionPlan getModulesEndpointExecutionPlan(Map<Stage, List<ModuleName>> modulesStages) {
        new EndpointExecutionPlan(
                stages: modulesStages.collectEntries { stage, moduleNames ->
                    [(stage): new StageExecutionPlan(
                            groups: moduleNames.collect { moduleName ->
                                ExecutionGroup.getModuleExecutionGroup(moduleName, stage)
                            }
                    )]
                } as Map<Stage, StageExecutionPlan>
        )
    }
}
