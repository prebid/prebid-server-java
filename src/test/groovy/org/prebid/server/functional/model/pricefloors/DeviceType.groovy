package org.prebid.server.functional.model.pricefloors

import com.fasterxml.jackson.annotation.JsonValue

enum DeviceType {

    DESKTOP("desktop"),
    PHONE("phone"),
    TABLET("tablet"),
    MULTIPLE("*")

    @JsonValue
    final String value

    DeviceType(String value) {
        this.value = value
    }

    @Override
    String toString() {
        value
    }
}
