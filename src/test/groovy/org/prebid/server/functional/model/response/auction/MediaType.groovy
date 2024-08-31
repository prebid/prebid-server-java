package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.annotation.JsonValue

enum MediaType {

    BANNER,
    VIDEO,
    AUDIO,
    NATIVE,
    WILDCARD,
    NULL

    @JsonValue
    String getValue() {
        if (name() == "NULL") {
            return null
        } else if (name() == "WILDCARD") {
            return "*"
        }
        name().toLowerCase()
    }
}
