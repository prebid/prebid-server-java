package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonValue

enum Realtime {

    REPLAY(0), REAL_TIME(1)

    @JsonValue
    final Integer value

    Realtime(Integer value) {
        this.value = value
    }
}
