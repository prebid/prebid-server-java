package org.prebid.server.functional.model

import com.fasterxml.jackson.annotation.JsonValue

enum Currency {

    USD, EUR, GBP, JPY, CHF, CAD, BOGUS

    @JsonValue
    String getValue() {
        name()
    }
}
