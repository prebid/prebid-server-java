package org.prebid.server.functional.model.config

import groovy.transform.ToString
import org.prebid.server.functional.model.ModuleName

@ToString(includeNames = true, ignoreNulls = true)
class StageExecutionPlan {

    List<ExecutionGroup> groups

    static StageExecutionPlan getModuleStageExecutionPlan(ModuleName name, Stage stage) {
        new StageExecutionPlan(groups: [ExecutionGroup.getModuleExecutionGroup(name, stage)])
    }
}
