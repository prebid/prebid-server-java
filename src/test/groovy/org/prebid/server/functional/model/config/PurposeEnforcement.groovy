package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonValue

enum PurposeEnforcement {

    NO, BASIC, FULL

    @JsonValue
    String getValue() {
        name().toLowerCase()
    }
}
