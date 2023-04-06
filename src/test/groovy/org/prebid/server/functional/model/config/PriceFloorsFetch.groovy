package org.prebid.server.functional.model.config

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.KebabCaseStrategy)
class PriceFloorsFetch {

    Boolean enabled
    String url
    Long timeoutMs
    Long maxFileSizeKb
    Integer maxRules
    Integer maxAgeSec
    Integer periodSec
}
