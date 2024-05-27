package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.annotation.JsonValue

enum ResponseAction {

    UPDATE, NO_ACTION

    @JsonValue
    String getValue() {
        name().toLowerCase()
    }
}
