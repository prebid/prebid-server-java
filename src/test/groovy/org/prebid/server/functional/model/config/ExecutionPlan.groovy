package org.prebid.server.functional.model.config

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.model.ModuleName

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
class ExecutionPlan {

    List<AbTest> abTests
    Map<Endpoint, EndpointExecutionPlan> endpoints

    static ExecutionPlan getSingleEndpointExecutionPlan(Endpoint endpoint, ModuleName moduleName, List<Stage> stage) {
        new ExecutionPlan(endpoints: [(endpoint): EndpointExecutionPlan.getModuleEndpointExecutionPlan(moduleName, stage)])
    }

    static ExecutionPlan getSingleEndpointExecutionPlan(Endpoint endpoint, Map<Stage, List<ModuleName>> modulesStages) {
        new ExecutionPlan(endpoints: [(endpoint): EndpointExecutionPlan.getModulesEndpointExecutionPlan(modulesStages)])
    }
}
