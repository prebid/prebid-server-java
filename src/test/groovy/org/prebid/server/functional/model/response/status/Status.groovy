package org.prebid.server.functional.model.response.status

import com.fasterxml.jackson.annotation.JsonValue

enum Status {

    OK

    @JsonValue
    String getValue() {
        name().toLowerCase()
    }
}
