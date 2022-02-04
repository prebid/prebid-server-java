package org.prebid.server.functional.model.request.event

import com.fasterxml.jackson.annotation.JsonValue

enum EventType {

    WIN, IMP

    @JsonValue
    String getValue() {
        name().toLowerCase()
    }
}
