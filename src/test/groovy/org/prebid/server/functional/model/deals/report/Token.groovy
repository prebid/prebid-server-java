package org.prebid.server.functional.model.deals.report

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class Token {

    @JsonProperty("class")
    Integer priorityClass

    Integer total

    Long spent

    Long totalSpent
}
