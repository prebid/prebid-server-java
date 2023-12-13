package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.annotation.JsonValue

enum MediaType {

    BANNER,
    VIDEO,
    AUDIO,
    NATIVE,
    NULL

    @JsonValue
    String getValue() {
        if (name() == "NULL") {
            return null
        }
        name().toLowerCase()
    }
}
