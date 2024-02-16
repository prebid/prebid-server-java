package org.prebid.server.functional.model.config.hook

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.KebabCaseStrategy)
class ExecutionGroup {

    Long timeout
    List<HookId> hookSequence

    static ExecutionGroup getModuleExecutionGroup(ModuleName name, Stage stage) {
        new ExecutionGroup(timeout: 100, hookSequence: [new HookId(moduleCode: name.code, hookImplCode: "${name.code}-${stage.value}-hook")])
    }
}
