package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.annotation.JsonValue

enum ErrorType {

    GENERAL("general"),
    GENERIC("generic"),
    GENER_X("gener_x"),
    GENERIC_CAMEL_CASE("GeNerIc"),
    RUBICON("rubicon"),
    APPNEXUS("appnexus"),
    PREBID("prebid"),
    CACHE("cache"),
    ALIAS("alias"),
    TARGETING("targeting"),
    IX("ix"),
    OPENX("openx"),
    AMX("amx"),
    AMX_UPPER_CASE("AMX"),

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
