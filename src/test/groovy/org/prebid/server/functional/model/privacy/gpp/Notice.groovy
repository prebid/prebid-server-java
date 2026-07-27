package org.prebid.server.functional.model.privacy.gpp

import com.fasterxml.jackson.annotation.JsonValue

enum Notice {

    NOT_APPLICABLE(0),
    PROVIDED(1),
    NOT_PROVIDED(2)

    @JsonValue
    final int value

    Notice(int value) {
        this.value = value
    }
}
