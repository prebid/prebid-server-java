package org.prebid.server.functional.model.request.cookiesync

import com.fasterxml.jackson.annotation.JsonValue

enum FilterType {

    INCLUDE, EXCLUDE

    @JsonValue
    String getValue() {
        name().toLowerCase()
    }
}
