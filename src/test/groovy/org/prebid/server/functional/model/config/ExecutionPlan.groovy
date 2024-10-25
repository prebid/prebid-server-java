package org.prebid.server.functional.model.config

import groovy.transform.ToString
import org.prebid.server.functional.model.ModuleName

@ToString(includeNames = true, ignoreNulls = true)
class ExecutionPlan {

    Map<Endpoint, EndpointExecutionPlan> endpoints

    static ExecutionPlan getEndpointExecutionPlan(Endpoint endpoint, ModuleName moduleName, List<Stage> stage) {
        new ExecutionPlan(endpoints: [(endpoint): EndpointExecutionPlan.getModuleEndpointExecutionPlan(moduleName, stage)])
    }

    static ExecutionPlan getEndpointExecutionPlan(Endpoint endpoint, Map<Stage, List<ModuleName>> modulesStages) {
        new ExecutionPlan(endpoints: [(endpoint): EndpointExecutionPlan.getModulesEndpointExecutionPlan(modulesStages)])
    }
}
