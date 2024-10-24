package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.annotation.JsonValue

enum BidMediaType {

    BANNER(1),
    VIDEO(2),
    AUDIO(3),
    NATIVE(4)

    @JsonValue
    final Integer value

    BidMediaType(Integer value) {
        this.value = value
    }
}
