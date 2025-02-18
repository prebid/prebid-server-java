package org.prebid.server.functional.model.privacy.gpp

import com.fasterxml.jackson.annotation.JsonValue

enum DataActivity {

    NOT_APPLICABLE(0),
    NO_CONSENT(1),
    CONSENT(2)

    @JsonValue
    final int value

    DataActivity(int value) {
        this.value = value
    }
}
