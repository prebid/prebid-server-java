package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonValue

enum Location {

    REQUEST("request"),
    FETCH("fetch"),
    NO_DATA("noData")

    @JsonValue
    String value

    Location(String value) {
        this.value = value
    }

    @Override
    String toString() {
        value
    }
}
