package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.annotation.JsonValue

enum ErrorType {

    GENERAL, GENERIC, RUBICON, APPNEXUS, PREBID

    @JsonValue
    String getValue() {
        name().toLowerCase()
    }
}
