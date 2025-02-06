package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.annotation.JsonValue

enum ResponseAction {

    UPDATE, NO_ACTION, NO_INVOCATION

    @JsonValue
    String getValue() {
        name().toLowerCase()
    }
}
