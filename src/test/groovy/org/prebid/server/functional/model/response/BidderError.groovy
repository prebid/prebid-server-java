package org.prebid.server.functional.model.response

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy)
class BidderError {

    BidderErrorCode code
    String message
    @JsonProperty("error")
    String errorMassage
    Set<String> impIds
}
