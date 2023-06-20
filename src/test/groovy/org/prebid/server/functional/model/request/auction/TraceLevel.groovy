package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonValue

enum TraceLevel {

    BASIC, VERBOSE

    @JsonValue
    String getValue() {
        name().toLowerCase()
    }
}
