package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonValue

enum ResultFunction {

    INCLUDE_BIDDERS("includeBidders"),
    EXCLUDE_BIDDER("excludeBidders"),
    LOG_A_TAG("logAtag")

    String value

    ResultFunction(String value) {
        this.value = value
    }

    @Override
    @JsonValue
    String toString() {
        return value
    }
}
