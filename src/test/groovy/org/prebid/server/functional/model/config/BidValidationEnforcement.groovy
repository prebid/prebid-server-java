package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonValue

enum BidValidationEnforcement {

    SKIP, ENFORCE, WARN

    @JsonValue
    String getValue() {
        name().toLowerCase()
    }
}
