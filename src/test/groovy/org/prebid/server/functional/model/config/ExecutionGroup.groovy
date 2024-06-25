package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.model.ModuleName

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.KebabCaseStrategy)
class ExecutionGroup {

    Long timeout
    List<HookId> hookSequence

    @JsonProperty("hook_sequence")
    List<HookId> hookSequenceSnakeCase

    static ExecutionGroup getModuleExecutionGroup(ModuleName name, Stage stage) {
        new ExecutionGroup().tap {
            timeout = 100
            hookSequence = [new HookId(moduleCode: name.code, hookImplCode: "${name.code}-${stage.value}-hook")]
        }
    }
}
