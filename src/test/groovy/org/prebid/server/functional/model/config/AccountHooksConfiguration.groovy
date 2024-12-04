package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.KebabCaseStrategy)
class AccountHooksConfiguration {

    ExecutionPlan executionPlan
    @JsonProperty("execution_plan")
    ExecutionPlan executionPlanSnakeCase
    PbsModulesConfig modules
    AdminConfig admin
}
