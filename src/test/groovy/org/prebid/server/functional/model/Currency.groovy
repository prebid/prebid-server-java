package org.prebid.server.functional.model

import com.fasterxml.jackson.annotation.JsonValue

enum Currency {

    USD, EUR, GBP, JPY, BOGUS

    @JsonValue
    String getValue() {
        name()
    }
}
