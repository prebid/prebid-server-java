package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonValue

enum RefType {

    UNKNOWN(0), USER_ACTION(1), EVENT(2), TIME(3)

    @JsonValue
    final Integer value

    RefType(Integer value) {
        this.value = value
    }
}
