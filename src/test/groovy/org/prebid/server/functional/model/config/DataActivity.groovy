package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonValue

enum DataActivity {
    NOT_APPLICABLE(0),
    CONSENT(1),
    NO_CONSENT(2),
    INVALID(-1),

    @JsonValue
    final int dataActivityBits

    DataActivity(int dataActivityBits) {
        this.dataActivityBits = dataActivityBits
    }

    static DataActivity fromInt(int dataActivityBits) {
        values().find { it.dataActivityBits == dataActivityBits }
                ?: { throw new IllegalArgumentException("Invalid dataActivityBits: ${dataActivityBits}") }
    }
}
