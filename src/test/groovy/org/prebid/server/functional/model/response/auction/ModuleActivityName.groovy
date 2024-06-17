package org.prebid.server.functional.model.response.auction

import com.fasterxml.jackson.annotation.JsonValue

enum ModuleActivityName {

    ORTB2_BLOCKING('enforce-blocking'),
    REJECT_RICHMEDIA('reject-richmedia')

    @JsonValue
    final String value

    private ModuleActivityName(String value) {
        this.value = value
    }
}
