package org.prebid.server.functional.model.config

import groovy.transform.ToString
import org.prebid.server.functional.model.ModuleName

@ToString(includeNames = true, ignoreNulls = true)
class ExecutionPlan {

    Map<Endpoint, EndpointExecutionPlan> endpoints

    static ExecutionPlan getSingleEndpointExecutionPlan(Endpoint endpoint, ModuleName moduleName, Stage stage) {
        new ExecutionPlan(endpoints: [(endpoint): EndpointExecutionPlan.getModuleEndpointExecutionPlan(moduleName, stage)])
    }
}
