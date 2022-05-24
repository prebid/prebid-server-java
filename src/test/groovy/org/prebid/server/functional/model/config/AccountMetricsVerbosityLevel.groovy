package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonValue

enum AccountMetricsVerbosityLevel {

    NONE, BASIC, DETAILED

    @JsonValue
    String getValue() {
        name().toLowerCase()
    }
}
