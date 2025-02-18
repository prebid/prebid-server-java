package org.prebid.server.functional.model.privacy.gpp

import com.fasterxml.jackson.annotation.JsonValue

enum Notice {

    NOT_APPLICABLE(0),
    YES(1),
    NO(2)

    @JsonValue
    final int value

    Notice(int value) {
        this.value = value
    }
}
