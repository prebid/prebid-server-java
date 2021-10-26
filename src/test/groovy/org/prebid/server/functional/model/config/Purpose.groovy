package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonValue

enum Purpose {

    P1, P2, P3, P4, P5, P6, P7, P8, P9, P10

    @JsonValue
    String getValue() {
        name().toLowerCase()
    }
}
