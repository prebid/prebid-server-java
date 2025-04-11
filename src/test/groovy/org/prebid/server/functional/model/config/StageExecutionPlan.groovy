package org.prebid.server.functional.model.config

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class StageExecutionPlan {

    List<ExecutionGroup> groups

    static StageExecutionPlan getModuleStageExecutionPlan(ModuleName name, Stage stage) {
        new StageExecutionPlan(groups: [ExecutionGroup.getModuleExecutionGroup(name, stage)])
    }

    static StageExecutionPlan getModuleStageExecutionPlan(List<ModuleHookImplementation> modulesHooks) {
        new StageExecutionPlan(groups: modulesHooks.collect { ExecutionGroup.getModuleExecutionGroup(it) })
    }

    StageExecutionPlan addGroup(ModuleHookImplementation moduleHook) {
        (groups ?: (groups = [])).add(ExecutionGroup.getModuleExecutionGroup(moduleHook))
        this
    }
}
