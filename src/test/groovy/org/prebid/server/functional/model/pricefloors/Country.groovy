package org.prebid.server.functional.model.pricefloors

import com.fasterxml.jackson.annotation.JsonValue

enum Country {

    USA("USA"),
    MULTIPLE("*")

    @JsonValue
    final String value

    Country(String value) {
        this.value = value
    }

    @Override
    String toString() {
        value
    }
}
