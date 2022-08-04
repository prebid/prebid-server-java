package org.prebid.server.functional.model.response.cookiesync

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class UsersyncInfo {

    String url
    Type type
    Boolean supportCORS

    enum Type {
        @JsonProperty("iframe")
        IFRAME,
        @JsonProperty("redirect")
        REDIRECT
    }
}
