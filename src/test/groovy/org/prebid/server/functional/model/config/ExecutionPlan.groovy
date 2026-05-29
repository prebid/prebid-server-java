package org.prebid.server.functional.model.config

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.model.ModuleName

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
class ExecutionPlan {

    List<AbTest> abTests
    Map<HookHttpEndpoint, EndpointExecutionPlan> endpoints

    static ExecutionPlan getSingleEndpointExecutionPlan(HookHttpEndpoint endpoint, ModuleName moduleName, List<Stage> stage) {
        new ExecutionPlan(endpoints: [(endpoint): EndpointExecutionPlan.getModuleEndpointExecutionPlan(moduleName, stage)])
    }

    static ExecutionPlan getSingleEndpointExecutionPlan(HookHttpEndpoint endpoint, Map<Stage, List<ModuleName>> modulesStages) {
        new ExecutionPlan(endpoints: [(endpoint): EndpointExecutionPlan.getModulesEndpointExecutionPlan(modulesStages)])
    }

    List<String> getListOfModuleCodes() {
        endpoints.values().stages*.values().groups.hookSequence.moduleCode.flatten() as List<String>
    }
}
