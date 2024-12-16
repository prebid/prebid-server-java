package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonValue

enum PaaFormant {

    ORIGINAL, IAB

    @JsonValue
    String getValue() {
        name().toLowerCase()
    }
}
