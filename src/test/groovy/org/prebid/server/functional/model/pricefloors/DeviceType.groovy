package org.prebid.server.functional.model.pricefloors

import com.fasterxml.jackson.annotation.JsonValue

enum DeviceType {

    DESKTOP, PHONE, TABLET, MULTIPLE

    @JsonValue
    String getValue() {
        name().toLowerCase()
    }
}
