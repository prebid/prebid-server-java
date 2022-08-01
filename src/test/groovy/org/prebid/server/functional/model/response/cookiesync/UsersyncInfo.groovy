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
        IFRAME("iframe", UsersyncFormat.BLINK),
        @JsonProperty("redirect")
        REDIRECT("redirect", UsersyncFormat.PIXEL)

        public final String name
        public final UsersyncFormat format

        Type(String name, UsersyncFormat format) {
            this.name = name
            this.format = format
        }
    }
}
