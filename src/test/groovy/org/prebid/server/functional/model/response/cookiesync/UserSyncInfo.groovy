package org.prebid.server.functional.model.response.cookiesync

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class UserSyncInfo {

    String url
    Type type
    Boolean supportCORS

    enum Type {

        IFRAME,
        REDIRECT

        @JsonValue
        String getValue() {
            name().toLowerCase()
        }
    }

    enum Format {

        PIXEL("i"),
        BLANK("b");

        final String name

        Format(String name) {
            this.name = name
        }

        @JsonValue
        String getValue() {
            name().toLowerCase()
        }
    }
}
