package org.prebid.server.functional.model.privacy.gpp

import com.fasterxml.jackson.annotation.JsonValue

enum MspaMode {

    NOT_APPLICABLE(0),
    YES(1),
    NO(2)

    @JsonValue
    final int value

    MspaMode(int value) {
        this.value = value
    }
}
