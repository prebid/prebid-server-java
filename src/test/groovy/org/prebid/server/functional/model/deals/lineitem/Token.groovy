package org.prebid.server.functional.model.deals.lineitem

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class Token {

    @JsonProperty("class")
    Integer priorityClass

    Integer total

    static getDefaultToken() {
        new Token(priorityClass: 1,
                total: 1000
        )
    }
}
