package org.prebid.server.functional.model.request.profile

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
enum ProfileType {

    EMPTY(""),
    REQUEST("request"),
    IMP("imp"),
    UNKNOWN("unknown")

    private final String value

    ProfileType(String value) {
        this.value = value
    }

    @JsonValue
    String getValue() {
        name().toLowerCase()
    }

    static ProfileType forValue(String value) {
        values().find { it.value == value }
    }
}
