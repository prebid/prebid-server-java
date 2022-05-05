package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.annotation.JsonValue

enum MediaType {

    BANNER, VIDEO, NATIVE

    @JsonValue
    String getValue() {
        name().toLowerCase()
    }
}
