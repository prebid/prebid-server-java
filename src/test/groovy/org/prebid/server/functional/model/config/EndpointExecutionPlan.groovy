package org.prebid.server.functional.model.config

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class EndpointExecutionPlan {

    Map<Stage, StageExecutionPlan> stages

    static EndpointExecutionPlan getModuleEndpointExecutionPlan(ModuleName name, List<Stage> stages) {
        new EndpointExecutionPlan(stages: stages.collectEntries {
            it -> [(it): StageExecutionPlan.getModuleStageExecutionPlan(name, it)]
        } as Map<Stage, StageExecutionPlan>)
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

    static EndpointExecutionPlan getModuleEndpointExecutionPlan(List<ModuleHookImplementation> modulesHooks) {
        Map<Stage, StageExecutionPlan> stages = [:]
        modulesHooks.each { moduleHook ->
            def stage = Stage.forValue(moduleHook)
            if (!stages.containsKey(stage)) {
                stages[stage] = StageExecutionPlan.getModuleStageExecutionPlan([moduleHook])
            } else {
                stages[stage].addGroup(moduleHook)
            }
        }
        new EndpointExecutionPlan(stages: stages)
    }
}
