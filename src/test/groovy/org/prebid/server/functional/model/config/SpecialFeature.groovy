package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonValue

enum SpecialFeature {

    SF1, SF2

    @JsonValue
    String getValue() {
        name().toLowerCase()
    }
}
