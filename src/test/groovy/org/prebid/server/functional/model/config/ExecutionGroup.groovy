package org.prebid.server.functional.model.config

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.model.ModuleName

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.KebabCaseStrategy)
class ExecutionGroup {

    Long timeout
    List<HookId> hookSequence

    static ExecutionGroup getModuleExecutionGroup(ModuleName name, Stage stage) {
        new ExecutionGroup(timeout: 100, hookSequence: [new HookId(moduleCode: name.code, hookImplCode: ModuleHookImplementation.forValue(name, stage).code)])
    }
}
