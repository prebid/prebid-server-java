package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.annotation.JsonValue

enum MediaType {

    BANNER, VIDEO

    @JsonValue
    String getValue() {
        name().toLowerCase()
    }
}
