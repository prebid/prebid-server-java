package org.prebid.server.functional.model.pricefloors

import com.fasterxml.jackson.annotation.JsonValue

enum Currency {

    USD, EUR

    @JsonValue
    String getValue() {
        name()
    }
}
