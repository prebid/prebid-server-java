package org.prebid.server.functional.model.privacy.gpp

import com.fasterxml.jackson.annotation.JsonValue

enum OptOut {

    NOT_APPLICABLE(0),
    OPTED_OUT(1),
    DID_NOT_OPT_OUT(2)

    @JsonValue
    final int value

    OptOut(int value) {
        this.value = value
    }
}
