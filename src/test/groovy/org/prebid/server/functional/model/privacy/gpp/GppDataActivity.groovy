package org.prebid.server.functional.model.privacy.gpp

import com.fasterxml.jackson.annotation.JsonValue

enum GppDataActivity {

    INVALID(-1),
    NOT_APPLICABLE(0),
    NO_CONSENT(1),
    CONSENT(2)

    @JsonValue
    final int value

    GppDataActivity(int value) {
        this.value = value
    }

    static GppDataActivity fromInt(int dataActivityBits) {
        values().find { it.value == dataActivityBits }
                ?: { throw new IllegalArgumentException("Invalid dataActivityBits: ${dataActivityBits}") } as GppDataActivity
    }
}
