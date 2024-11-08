package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonValue

enum DebugCondition {

    DISABLED(0), ENABLED(1)

    @JsonValue
    final int value

    private DebugCondition(int value) {
        this.value = value
    }
}
