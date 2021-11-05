package org.prebid.server.functional.model

import com.fasterxml.jackson.annotation.JsonValue

enum AccountStatus {

    ACTIVE, INACTIVE

    @JsonValue
    String getValue() {
        name().toLowerCase()
    }

    static AccountStatus forValue(String value) {
        values().find { it.value == value }
    }
}
