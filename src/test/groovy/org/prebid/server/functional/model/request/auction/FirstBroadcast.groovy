package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonValue

enum FirstBroadcast {

    NOT_FIRST_BROADCAST(0), FIRST_BROADCAST(1)

    @JsonValue
    final Integer value

    FirstBroadcast(Integer value) {
        this.value = value
    }
}
