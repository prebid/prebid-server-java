package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.model.config.HookId

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
class InvocationResult {

    Long executionTimeMillis
    InvocationStatus status
    ResponseAction action
    HookId hookId
    AnalyticsPrebidTag analyticsTags
    String message
}
