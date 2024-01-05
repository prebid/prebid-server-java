package org.prebid.server.functional.model.config

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.model.request.auction.PrebidModulesConfig

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.KebabCaseStrategy)
class AccountHooksConfiguration {

    ExecutionPlan executionPlan
    PrebidModulesConfig modules
}
