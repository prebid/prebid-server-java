package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.ToString
import org.prebid.server.functional.model.response.cookiesync.UserSync

@ToString(includeNames = true, ignoreNulls = true)
class ResponseSyncData {

    CookieStatus status
    List<UserSync> syncs

    enum CookieStatus {

        NONE, EXPIRED, AVAILABLE

        @JsonValue
        String getValue() {
            name().toLowerCase()
        }
    }
}
