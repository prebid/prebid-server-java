package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.KebabCaseStrategy)
class ExecutionGroup {

    Long timeout
    List<HookId> hookSequence

    @JsonProperty("hook_sequence")
    List<HookId> hookSequenceSnakeCase

    static ExecutionGroup getModuleExecutionGroup(ModuleHookImplementation moduleHook) {
        new ExecutionGroup().tap {
            timeout = 100
            hookSequence = [new HookId(moduleCode: ModuleName.forValue(moduleHook).code, hookImplCode: moduleHook.code)]
        }
    }

    static ExecutionGroup getModuleExecutionGroup(ModuleName name, Stage stage) {
        new ExecutionGroup().tap {
            timeout = 100
            hookSequence = [new HookId(moduleCode: name.code, hookImplCode: ModuleHookImplementation.forValue(name, stage).code)]
        }
    }
}
