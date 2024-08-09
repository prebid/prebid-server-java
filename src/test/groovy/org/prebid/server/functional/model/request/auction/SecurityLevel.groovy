package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonValue

enum SecurityLevel {

    NON_SECURE(0), SECURE(1)

    @JsonValue
    private final Integer level

    SecurityLevel(int level) {
        this.level = level
    }
}
