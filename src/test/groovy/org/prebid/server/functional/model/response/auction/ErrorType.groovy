package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.annotation.JsonValue

enum ErrorType {

    GENERAL("general"),
    GENERIC("generic"),
    GENERIC_CAMEL_CASE("GeNerIc"),
    RUBICON("rubicon"),
    APPNEXUS("appnexus"),
    PREBID("prebid"),
    CACHE("cache"),
    ALIAS("alias"),
    TARGETING("targeting")

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
