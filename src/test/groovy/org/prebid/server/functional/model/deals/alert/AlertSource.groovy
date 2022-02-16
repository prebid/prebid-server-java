package org.prebid.server.functional.model.deals.alert

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.KebabCaseStrategy)
class AlertSource {

    String env
    String dataCenter
    String region
    String system
    String subSystem
    String hostId
}
