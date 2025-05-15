package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonValue

enum BidRounding {

    UP("up"),
    DOWN("down"),
    TRUE("true"),
    TIME_SPLIT("timesplit"),
    UNKNOWN("unknown"),

    private String value

    BidRounding(String value) {
        this.value = value
    }

    @Override
    @JsonValue
    String toString() {
        return value
    }
}
