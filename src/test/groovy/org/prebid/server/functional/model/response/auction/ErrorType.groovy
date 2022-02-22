package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.annotation.JsonValue

enum ErrorType {

    GENERAL, GENERIC, RUBICON, APPNEXUS, PREBID, CACHE

    @JsonValue
    String getValue() {
        name().toLowerCase()
    }
}
