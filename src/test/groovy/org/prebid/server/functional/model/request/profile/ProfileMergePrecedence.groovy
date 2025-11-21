package org.prebid.server.functional.model.request.profile

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
enum ProfileMergePrecedence {

    EMPTY(""),
    REQUEST("request"),
    PROFILE("profile"),
    UNKNOWN("unknown")

    private final String value

    ProfileMergePrecedence(String value) {
        this.value = value
    }

    @JsonValue
    String getValue() {
        name().toLowerCase()
    }

    static ProfileMergePrecedence forValue(String value) {
        values().find { it.value == value }
    }
}
