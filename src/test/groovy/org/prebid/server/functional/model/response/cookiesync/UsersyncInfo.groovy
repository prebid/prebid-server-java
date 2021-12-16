package org.prebid.server.functional.model.response.cookiesync

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class UsersyncInfo {

    String url
    Type type
    Boolean supportCORS

    enum Type {

        REDIRECT, IFRAME

        @JsonValue
        String getValue() {
            name().toLowerCase()
        }
    }
}
