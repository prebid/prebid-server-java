package org.prebid.server.functional.model.request.profile

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
enum ProfileType {

    REQUEST, IMP

    @JsonValue
    String getValue() {
        name().toLowerCase()
    }

    static ProfileType forValue(String value) {
        values().find { it.value == value }
    }
}
