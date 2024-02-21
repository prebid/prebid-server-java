package org.prebid.server.functional.model.config.hook

import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class StageExecutionPlan {

    List<ExecutionGroup> groups

    static StageExecutionPlan getModuleStageExecutionPlan(ModuleName name, Stage stage) {
        new StageExecutionPlan(groups: [ExecutionGroup.getModuleExecutionGroup(name, stage)])
    }
}
