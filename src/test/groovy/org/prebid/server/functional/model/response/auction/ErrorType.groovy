package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.annotation.JsonValue

enum ErrorType {

    GENERAL("general"),
    GENERIC("generic"),
    RUBICON("rubicon"),
    APPNEXUS("appnexus"),
    PREBID("prebid"),
    CACHE("cache"),
    GENERIC_ALIAS("genericAlias")

    @JsonValue
    final String value

    ErrorType(String value) {
        this.value = value
    }

    @Override
    String toString() {
        value
    }
}
