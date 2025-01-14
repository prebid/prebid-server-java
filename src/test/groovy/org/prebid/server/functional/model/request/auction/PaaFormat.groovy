package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonValue

enum PaaFormat {

    ORIGINAL, IAB

    @JsonValue
    String getValue() {
        name().toLowerCase()
    }
}
