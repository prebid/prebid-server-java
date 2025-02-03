package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@EqualsAndHashCode
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy)
class And {

    List<String> and
    String privacyModule
    String skipped
    String result
}
