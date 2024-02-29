package org.prebid.server.functional.model.pricefloors

import com.fasterxml.jackson.annotation.JsonValue
import org.prebid.server.functional.util.privacy.model.State

enum Country {

    USA("USA"),
    CAN("CAN"),
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

    String withState(State state) {
        return "${value}.${state.abbreviation}".toString()
    }
}
