package org.prebid.server.functional.model.privacy.gpp

import com.fasterxml.jackson.annotation.JsonValue

enum GpcSubsectionType {

    CORE(0),
    GPC(1)

    @JsonValue
    final int value

    GpcSubsectionType(int value) {
        this.value = value
    }
}
