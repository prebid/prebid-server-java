package org.prebid.server.functional.model.response.cookiesync

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class UserSync {

    String url
    UserSyncType type

    enum UserSyncType {

        IFRAME, PIXEL

        @JsonValue
        String getValue() {
            name().toLowerCase()
        }
    }
}
