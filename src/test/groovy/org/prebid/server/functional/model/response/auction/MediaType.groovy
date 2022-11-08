package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.annotation.JsonValue

enum MediaType {

    BANNER,
    VIDEO,
    AUDIO,
    NATIVE

    @JsonValue
    String getValue() {
        name().toLowerCase()
    }
}
